-- ---------------------------------------------------------------------------
-- A procedência de cada tabela de uma carga de catálogo.
--
-- TABELA VAZIA. Nenhum INSERT, nenhum dado normativo, como em toda migration
-- deste repositório.
--
-- POR QUE ISTO EXISTE
--
-- O sistema precisa avisar quem lê quando está exibindo dado de demonstração.
-- A alternativa considerada e recusada era uma propriedade de instalação do
-- tipo "auditoria.demonstracao=true": ela é promessa de quem configurou, e quem
-- esquecesse de ligá-la veria dado ficticio apresentado como norma vigente --
-- que e exatamente o modo de falha que a marcacao existe para evitar.
--
-- Aqui a procedencia viaja com o dado: declarada linha a linha na coluna
-- "natureza" de cada CSV de dados, conferida na importacao e gravada junto da
-- carga. Nao ha o que esquecer de ligar.
--
-- POR QUE POR TABELA, E NAO UMA COLUNA EM "carga_catalogo"
--
-- Por causa do caso misto: alguem carregar um anexo real e o resto ficticio.
-- Um sinalizador unico teria de escolher entre chamar a carga de real ou de
-- ficticia, e as duas respostas estariam erradas. Por tabela, a derivacao diz
-- "parcialmente ficticio" e lista quais tabelas -- e quem le sabe em que parte
-- da tela pode confiar.
--
-- AUSENCIA DE LINHA E UM ESTADO, E NAO UM PADRAO
--
-- Carga importada antes desta etapa nao tem linha aqui, e isso NAO e lido como
-- "normativo". A tela diz que a procedencia nao foi declarada e pede
-- reimportacao. Supor que dado de origem desconhecida e norma vigente seria a
-- afirmacao mais cara que este sistema poderia fazer por engano.
--
-- Tabela sem registro tambem nao tem linha aqui: a natureza e declarada linha a
-- linha, e arquivo fornecido so com o cabecalho nao tem onde declara-la.
-- ---------------------------------------------------------------------------

create table natureza_da_carga (
    carga_id uuid not null,
    tabela   text not null,
    natureza text not null,

    constraint natureza_da_carga_pk primary key (carga_id, tabela),
    constraint natureza_da_carga_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    constraint natureza_da_carga_tabela_preenchida check (btrim(tabela) <> ''),
    constraint natureza_da_carga_preenchida check (btrim(natureza) <> '')
);
