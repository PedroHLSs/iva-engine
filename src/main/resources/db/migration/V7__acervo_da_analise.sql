-- ---------------------------------------------------------------------------
-- O que cada análise leu: os itens que passaram por ela e os arquivos que não
-- puderam ser lidos.
--
-- TABELAS VAZIAS. Nenhum INSERT, nenhum dado normativo, como em toda migration
-- deste repositório.
--
-- POR QUE ESTAS DUAS TABELAS PRECISAM EXISTIR
--
-- Até aqui, "execucao_auditoria" guarda apenas contagens, e as únicas linhas
-- por item são "achado" e "avaliacao_nao_concluida" — as duas escritas somente
-- quando houve o que registrar. Um documento cujos itens foram todos conformes
-- não deixa linha nenhuma.
--
-- Isso bastava enquanto a saída era o papel de trabalho, que é uma lista de
-- apontamentos. Deixa de bastar quando a pergunta passa a ser "quais são os
-- produtos desta nota, e qual a situação de cada um": derivar a lista dos
-- apontamentos devolveria só os produtos com problema, e uma nota inteiramente
-- conforme sumiria do resultado da própria análise que a leu.
-- ---------------------------------------------------------------------------

-- Os itens que uma análise efetivamente leu, com ou sem apontamento.
--
-- O hash_item guardado aqui é o que ESTA análise leu. O item em si continua em
-- "item_documento", que é sobrescrito a cada reprocessamento (D006): se a mesma
-- nota for enviada de novo com conteúdo diferente, a linha de lá muda e esta
-- não. Quando os dois hashes divergem, o item foi reprocessado depois desta
-- análise, e quem lê precisa saber disso em vez de receber calado o valor novo
-- sob a data antiga.
create table item_da_execucao (
    id           uuid        not null,
    execucao_id  uuid        not null,
    chave_acesso varchar(44) not null,
    numero_item  integer     not null,
    hash_item    varchar(64) not null,

    constraint item_da_execucao_pk primary key (id),
    constraint item_da_execucao_unico unique (execucao_id, chave_acesso, numero_item),
    constraint item_da_execucao_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint item_da_execucao_item_fk foreign key (chave_acesso, numero_item)
        references item_documento (chave_acesso, numero_item) on delete cascade,
    constraint item_da_execucao_numero_valido check (numero_item >= 1),
    constraint item_da_execucao_hash_hexadecimal check (hash_item ~ '^[0-9a-f]{64}$')
);

create index item_da_execucao_por_execucao on item_da_execucao (execucao_id);
create index item_da_execucao_por_hash on item_da_execucao (hash_item);

-- Arquivos que a análise não conseguiu ler.
--
-- ARQUIVO ILEGÍVEL NÃO É NOTA SEM DIVERGÊNCIA: é ausência, e aparece contado
-- à parte, nunca somado a nenhum dos quatro estados da interface.
--
-- Ficavam só em memória até aqui, o que bastava para a CLI — o comando imprime
-- o resumo e o processo encerra. Não basta para o histórico: um lote com três
-- arquivos ilegíveis, reaberto depois de o servidor reiniciar, apareceria
-- completo.
--
-- A COLUNA "origem" NÃO PODE CONTER CHAVE DE ACESSO.
--
-- Arquivo de NF-e costuma se chamar pelo número da chave — "<44 dígitos>-nfe.xml"
-- é o padrão que a maioria dos ERP exporta —, e os dígitos intermediários da
-- chave carregam o CNPJ do emitente (D005). Gravar o nome como veio traria o
-- identificador de volta para dentro do banco pela porta dos fundos, num
-- esquema cujo V2 declara não ter nenhuma coluna de dado pessoal.
--
-- Quem grava substitui a corrida de dígitos pelo pseudônimo antes de chegar
-- aqui; a restrição abaixo é a barreira de última instância, no mesmo espírito
-- das restrições de formato do pseudônimo em "documento".
create table falha_de_leitura_da_execucao (
    id            uuid    not null,
    execucao_id   uuid    not null,
    ordem         integer not null,
    -- Nome do arquivo ou da entrada do pacote, já sem identificador em texto claro.
    origem        text    not null,
    -- Classe da exceção, como FalhaDeLeitura a registrou.
    tipo_de_erro  text    not null,
    motivo        text    not null,

    constraint falha_de_leitura_da_execucao_pk primary key (id),
    constraint falha_de_leitura_da_execucao_unica unique (execucao_id, ordem),
    constraint falha_de_leitura_da_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint falha_de_leitura_da_execucao_ordem_valida check (ordem >= 0),
    constraint falha_de_leitura_da_execucao_origem_preenchida check (btrim(origem) <> ''),
    constraint falha_de_leitura_da_execucao_tipo_preenchido check (btrim(tipo_de_erro) <> ''),
    constraint falha_de_leitura_da_execucao_motivo_preenchido check (btrim(motivo) <> ''),
    constraint falha_de_leitura_da_execucao_sem_chave_em_texto_claro
        check (origem !~ '[0-9]{44}')
);

create index falha_de_leitura_da_execucao_por_execucao
    on falha_de_leitura_da_execucao (execucao_id);
