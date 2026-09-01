-- ---------------------------------------------------------------------------
-- Catálogo normativo.
--
-- TODAS as tabelas nascem VAZIAS. Não há INSERT de dado normativo nesta
-- migration, nem em nenhuma outra, em nenhum ambiente. Alíquota, código de
-- classificação tributária, par CST x cClassTrib, vínculo NCM x anexo e data de
-- vigência entram exclusivamente por importação de CSV em tempo de execução
-- (CLAUDE.md seção 5, decisão D003).
--
-- Códigos de origem normativa são "text", sem limite de tamanho: fixar um
-- varchar(N) seria afirmar quantos caracteres a norma admite, e um N pequeno
-- rejeitaria carga válida. Os poucos campos com tamanho fixo são os que o
-- próprio domínio já delimita desde a etapa 1 (NCM com 8 dígitos, CFOP com 4).
--
-- Percentuais e valores monetários usam "numeric" sem precisão declarada:
-- PostgreSQL preserva a escala exata do valor gravado, e para a auditoria
-- "0" e "0,00" são registros diferentes do mesmo número.
-- ---------------------------------------------------------------------------

-- Cada importação de catálogo é uma carga identificada. A auditoria usa a carga
-- mais recente e registra a versão dela em execucao_auditoria.versao_catalogo,
-- de modo que um relatório antigo sempre diz sobre qual catálogo foi produzido.
create table carga_catalogo (
    id           uuid        not null,
    versao       text        not null,
    importado_em timestamptz not null,

    constraint carga_catalogo_pk primary key (id),
    constraint carga_catalogo_versao_unica unique (versao)
);

-- Cobertura declarada da carga, uma linha por tabela normativa.
--
-- É o que permite distinguir "o catálogo não traz este registro" (apontamento)
-- de "esta tabela não foi carregada para a data do documento" (não avaliado).
-- Quem importa declara a cobertura; o sistema não a deduz.
create table cobertura_catalogo (
    carga_id        uuid not null,
    tabela          text not null,
    vigencia_inicio date not null,
    vigencia_fim    date,
    fonte_normativa text not null,

    constraint cobertura_catalogo_pk primary key (carga_id, tabela),
    constraint cobertura_catalogo_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    constraint cobertura_catalogo_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio)
);

create table classificacao_tributaria (
    id                     uuid    not null,
    carga_id               uuid    not null,
    codigo                 text    not null,
    dispositivo_legal      text    not null,
    indicador_de_beneficio boolean not null,
    percentual_reducao     numeric,
    vigencia_inicio        date    not null,
    vigencia_fim           date,
    fonte_normativa        text    not null,

    constraint classificacao_tributaria_pk primary key (id),
    constraint classificacao_tributaria_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    -- Duas versões do mesmo código na mesma carga só se distinguem pela
    -- vigência. Sobreposição de vigência é recusada na importação, antes de
    -- chegar aqui (SerieNormativa, D003).
    constraint classificacao_tributaria_versao_unica unique (carga_id, codigo, vigencia_inicio),
    constraint classificacao_tributaria_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio)
);

create index classificacao_tributaria_por_codigo
    on classificacao_tributaria (carga_id, codigo);

-- CSTs que a classificação admite. Conjunto, sem ordem significativa.
create table classificacao_tributaria_cst (
    classificacao_id uuid not null,
    cst              text not null,

    constraint classificacao_tributaria_cst_pk primary key (classificacao_id, cst),
    constraint classificacao_tributaria_cst_fk foreign key (classificacao_id)
        references classificacao_tributaria (id) on delete cascade
);

-- Campos que a classificação torna obrigatórios. Lista: a ordem da carga é
-- preservada para que a mensagem do apontamento saia sempre igual.
create table classificacao_tributaria_campo_obrigatorio (
    classificacao_id uuid    not null,
    ordem            integer not null,
    campo            text    not null,

    constraint classificacao_tributaria_campo_pk primary key (classificacao_id, ordem),
    constraint classificacao_tributaria_campo_fk foreign key (classificacao_id)
        references classificacao_tributaria (id) on delete cascade,
    constraint classificacao_tributaria_campo_ordem_valida check (ordem >= 0)
);

create table registro_ncm (
    id              uuid       not null,
    carga_id        uuid       not null,
    ncm             varchar(8) not null,
    descricao       text       not null,
    vigencia_inicio date       not null,
    vigencia_fim    date,
    fonte_normativa text       not null,

    constraint registro_ncm_pk primary key (id),
    constraint registro_ncm_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    constraint registro_ncm_versao_unica unique (carga_id, ncm, vigencia_inicio),
    constraint registro_ncm_oito_digitos check (ncm ~ '^[0-9]{8}$'),
    constraint registro_ncm_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio)
);

create index registro_ncm_por_ncm on registro_ncm (carga_id, ncm);

create table item_anexo (
    id                   uuid       not null,
    carga_id             uuid       not null,
    ncm                  varchar(8) not null,
    identificador_anexo  text       not null,
    tipo_de_tratamento   text       not null,
    vigencia_inicio      date       not null,
    vigencia_fim         date,
    fonte_normativa      text       not null,

    constraint item_anexo_pk primary key (id),
    constraint item_anexo_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    constraint item_anexo_versao_unica unique (carga_id, ncm, identificador_anexo, vigencia_inicio),
    constraint item_anexo_oito_digitos check (ncm ~ '^[0-9]{8}$'),
    constraint item_anexo_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio)
);

create index item_anexo_por_ncm on item_anexo (carga_id, ncm);

create table aliquota_vigente (
    id              uuid    not null,
    carga_id        uuid    not null,
    tributo         text    not null,
    percentual      numeric not null,
    abrangencia     text    not null,
    vigencia_inicio date    not null,
    vigencia_fim    date,
    fonte_normativa text    not null,

    constraint aliquota_vigente_pk primary key (id),
    constraint aliquota_vigente_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    constraint aliquota_vigente_versao_unica unique (carga_id, tributo, abrangencia, vigencia_inicio),
    constraint aliquota_vigente_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio)
);

create index aliquota_vigente_por_tributo on aliquota_vigente (carga_id, tributo);
