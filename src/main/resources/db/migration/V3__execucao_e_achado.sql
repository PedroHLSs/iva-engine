-- ---------------------------------------------------------------------------
-- Execução de auditoria e apontamentos.
--
-- Uma execução é o recibo de uma rodada: diz o que entrou (hash do conjunto de
-- arquivos), com que catálogo e com que conjunto de regras foi auditado, quantos
-- documentos e itens passaram, e quantos apontamentos saíram por severidade e
-- por regra. Sem esses campos o relatório não é reproduzível.
--
-- O apontamento, por outro lado, não pertence a uma execução: ele é identificado
-- pelo que aponta (hash do item + regra + versão da regra). Reprocessar o mesmo
-- lote não cria linha nova — atualiza a última execução que o produziu. É isso
-- que faz a tratativa sobreviver ao reprocessamento (D006).
-- ---------------------------------------------------------------------------

create table execucao_auditoria (
    id                     uuid        not null,
    data_hora              timestamptz not null,
    -- Resumo do conjunto de arquivos processados: mesmo lote, mesmo hash.
    hash_entrada           varchar(64) not null,
    versao_catalogo        text        not null,
    versao_conjunto_regras text        not null,
    quantidade_documentos  integer     not null,
    quantidade_itens       integer     not null,

    constraint execucao_auditoria_pk primary key (id),
    constraint execucao_auditoria_hash_hexadecimal check (hash_entrada ~ '^[0-9a-f]{64}$'),
    constraint execucao_auditoria_documentos_nao_negativo check (quantidade_documentos >= 0),
    constraint execucao_auditoria_itens_nao_negativo check (quantidade_itens >= 0)
);

create index execucao_auditoria_por_data_hora on execucao_auditoria (data_hora);

-- Contagem de apontamentos por severidade, uma linha por severidade avaliada,
-- inclusive as que ficaram em zero: relatório com severidade omitida obriga o
-- leitor a adivinhar se não houve apontamento ou se ninguém olhou.
create table execucao_achado_por_severidade (
    execucao_id uuid    not null,
    severidade  text    not null,
    quantidade  integer not null,

    constraint execucao_achado_por_severidade_pk primary key (execucao_id, severidade),
    constraint execucao_achado_por_severidade_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint execucao_achado_por_severidade_nao_negativo check (quantidade >= 0)
);

-- Contagem de apontamentos por regra, uma linha por regra aplicada, inclusive as
-- que não apontaram nada.
create table execucao_achado_por_regra (
    execucao_id uuid    not null,
    regra_id    text    not null,
    quantidade  integer not null,

    constraint execucao_achado_por_regra_pk primary key (execucao_id, regra_id),
    constraint execucao_achado_por_regra_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint execucao_achado_por_regra_nao_negativo check (quantidade >= 0)
);

create table achado (
    id                   uuid        not null,

    -- Identidade do apontamento. Mesmo item, mesma regra, mesma versão de
    -- regra: mesmo apontamento, não importa quantas vezes o lote rode.
    hash_item            varchar(64) not null,
    regra_id             text        not null,
    regra_versao         text        not null,

    severidade           text        not null,
    chave_acesso         varchar(44) not null,
    numero_item          integer     not null,
    fundamento_normativo text        not null,
    vigencia_inicio      date        not null,
    vigencia_fim         date,

    -- Ou há montante, ou há o motivo de não haver. Nunca nenhum dos dois, nunca
    -- os dois: ausência silenciosa de valor é o que a restrição abaixo impede.
    valor_em_risco       numeric,
    motivo_valor_ausente text,

    primeira_execucao_id uuid        not null,
    ultima_execucao_id   uuid        not null,
    detectado_em         timestamptz not null,
    visto_em             timestamptz not null,

    constraint achado_pk primary key (id),
    constraint achado_identidade unique (hash_item, regra_id, regra_versao),
    constraint achado_item_fk foreign key (chave_acesso, numero_item)
        references item_documento (chave_acesso, numero_item) on delete cascade,
    constraint achado_primeira_execucao_fk foreign key (primeira_execucao_id)
        references execucao_auditoria (id),
    constraint achado_ultima_execucao_fk foreign key (ultima_execucao_id)
        references execucao_auditoria (id),
    constraint achado_numero_item_valido check (numero_item >= 1),
    constraint achado_hash_hexadecimal check (hash_item ~ '^[0-9a-f]{64}$'),
    constraint achado_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio),
    constraint achado_valor_em_risco_explicado
        check ((valor_em_risco is not null and motivo_valor_ausente is null)
               or (valor_em_risco is null and motivo_valor_ausente is not null))
);

create index achado_por_regra on achado (regra_id, regra_versao);
create index achado_por_severidade on achado (severidade);
create index achado_por_documento on achado (chave_acesso);
create index achado_por_ultima_execucao on achado (ultima_execucao_id);

-- O que a regra olhou e o que encontrou. Sem evidência o apontamento não é
-- conferível, então todo apontamento tem ao menos uma linha aqui.
--
-- valor_encontrado nulo significa "o campo não veio no documento";
-- valor_esperado nulo significa "a regra não tinha referência a opor".
-- Texto vazio dentro dessas colunas é outra coisa: campo que veio em branco.
create table achado_evidencia (
    achado_id             uuid    not null,
    ordem                 integer not null,
    campo_analisado       text    not null,
    valor_encontrado      text,
    valor_esperado        text,
    -- Variante de OrigemEvidencia: DO_DOCUMENTO, DE_TABELA_NORMATIVA ou DA_REGRA.
    origem_tipo           text    not null,
    -- Localização no documento, nome da tabela ou descrição da derivação.
    origem_primeiro_termo text    not null,
    -- Só a origem de tabela normativa tem segundo termo: a versão da tabela.
    origem_segundo_termo  text,

    constraint achado_evidencia_pk primary key (achado_id, ordem),
    constraint achado_evidencia_achado_fk foreign key (achado_id)
        references achado (id) on delete cascade,
    constraint achado_evidencia_ordem_valida check (ordem >= 0),
    constraint achado_evidencia_segundo_termo_so_de_tabela
        check ((origem_tipo = 'DE_TABELA_NORMATIVA' and origem_segundo_termo is not null)
               or (origem_tipo <> 'DE_TABELA_NORMATIVA' and origem_segundo_termo is null))
);
