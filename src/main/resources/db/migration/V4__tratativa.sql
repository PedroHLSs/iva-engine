-- ---------------------------------------------------------------------------
-- Tratativa: a decisão humana sobre um apontamento.
--
-- Um apontamento pode ser ACEITO (o auditor concorda que há incoerência) ou
-- REFUTADO (o auditor sustenta que o documento está correto), sempre com
-- justificativa. Justificativa vazia não é aceita: uma tratativa sem razão
-- registrada apaga o apontamento sem deixar rastro do porquê.
--
-- A CHAVE É DE CONTEÚDO, NÃO DE LINHA: (hash_item, regra_id, regra_versao).
--
--   * Não há chave estrangeira para "achado". É deliberado. A tratativa precisa
--     sobreviver ao apagamento e à recriação da linha do apontamento, e é isso
--     que a torna imune a reprocessamento: rodar o mesmo lote de novo gera o
--     mesmo hash de item para a mesma regra, a tratativa é reencontrada e o
--     apontamento já sai tratado.
--
--   * regra_versao FAZ PARTE DA CHAVE, DE PROPÓSITO. Se a regra mudar de versão,
--     a tratativa registrada na versão anterior NÃO se aplica, e o apontamento
--     REABRE. A justificativa foi dada contra um critério; critério novo é
--     pergunta nova. A tratativa antiga não é apagada — fica no histórico,
--     amarrada à versão em que foi dada.
--
--   * Alterar o conteúdo do item também muda o hash e reabre o apontamento, pelo
--     mesmo motivo.
-- ---------------------------------------------------------------------------

create table tratativa (
    id            uuid        not null,
    hash_item     varchar(64) not null,
    regra_id      text        not null,
    regra_versao  text        not null,
    decisao       text        not null,
    justificativa text        not null,
    registrado_em timestamptz not null,

    constraint tratativa_pk primary key (id),
    constraint tratativa_unica unique (hash_item, regra_id, regra_versao),
    constraint tratativa_hash_hexadecimal check (hash_item ~ '^[0-9a-f]{64}$'),
    constraint tratativa_decisao_conhecida check (decisao in ('ACEITO', 'REFUTADO')),
    constraint tratativa_justificativa_preenchida check (btrim(justificativa) <> '')
);

create index tratativa_por_hash_item on tratativa (hash_item);
