-- ---------------------------------------------------------------------------
-- Documentos auditados e seus itens.
--
-- NENHUM DADO PESSOAL EM TEXTO CLARO. Não há coluna de CNPJ, CPF, razão social
-- nem endereço, e não deve haver. Emitente e destinatário entram apenas como
-- resumo criptográfico de 256 bits, calculado na infraestrutura com o sal de
-- instalação (D005). As restrições de formato abaixo existem justamente para
-- que um CNPJ gravado por engano nessas colunas seja recusado pelo banco.
--
-- AUSENTE E ZERO SÃO ESTADOS DIFERENTES. Toda coluna que corresponde a um
-- Optional do domínio é anulável, e nulo significa "o documento não declarou o
-- campo". Nunca se grava 0 no lugar de nulo: omitir base de cálculo e declarar
-- base zero são fatos fiscais distintos e produzem apontamentos distintos.
-- ---------------------------------------------------------------------------

create table documento (
    chave_acesso                varchar(44) not null,
    modelo                      text        not null,
    serie                       text        not null,
    numero                      text        not null,
    data_emissao                date        not null,
    uf_emitente                 varchar(2)  not null,
    uf_destinatario             varchar(2),
    crt_emitente                text,
    indicador_destinatario      text,
    emitente_pseudonimizado     varchar(64) not null,
    destinatario_pseudonimizado varchar(64),
    registrado_em               timestamptz not null,

    constraint documento_pk primary key (chave_acesso),
    constraint documento_chave_com_44_digitos check (chave_acesso ~ '^[0-9]{44}$'),
    -- Barreira de última instância contra dado pessoal: nenhum CNPJ, CPF, nome
    -- ou logradouro satisfaz 64 caracteres hexadecimais minúsculos.
    constraint documento_emitente_pseudonimizado
        check (emitente_pseudonimizado ~ '^[0-9a-f]{64}$'),
    constraint documento_destinatario_pseudonimizado
        check (destinatario_pseudonimizado is null
               or destinatario_pseudonimizado ~ '^[0-9a-f]{64}$')
);

create index documento_por_data_emissao on documento (data_emissao);

create table item_documento (
    id                              uuid        not null,
    chave_acesso                    varchar(44) not null,
    numero_item                     integer     not null,
    -- Resumo do conteúdo declarado do item. É a parte estável da chave de
    -- tratativa: sobrevive a reprocessamento porque depende do que o documento
    -- diz, e não da linha em que ele foi gravado (D006).
    hash_item                       varchar(64) not null,

    ncm                             varchar(8),
    cfop                            varchar(4),
    valor_item                      numeric     not null,

    cst_ibs                         text,
    cst_cbs                         text,
    codigo_classificacao_tributaria text,
    base_calculo_ibs                numeric,
    base_calculo_cbs                numeric,
    aliquota_ibs_uf                 numeric,
    aliquota_ibs_municipal          numeric,
    aliquota_cbs                    numeric,
    valor_ibs_uf                    numeric,
    valor_ibs_municipal             numeric,
    valor_cbs                       numeric,

    constraint item_documento_pk primary key (id),
    constraint item_documento_documento_fk foreign key (chave_acesso)
        references documento (chave_acesso) on delete cascade,
    constraint item_documento_unico unique (chave_acesso, numero_item),
    constraint item_documento_numero_valido check (numero_item >= 1),
    constraint item_documento_hash_hexadecimal check (hash_item ~ '^[0-9a-f]{64}$'),
    constraint item_documento_ncm_oito_digitos check (ncm is null or ncm ~ '^[0-9]{8}$'),
    constraint item_documento_cfop_quatro_digitos check (cfop is null or cfop ~ '^[0-9]{4}$')
);

create index item_documento_por_hash on item_documento (hash_item);
