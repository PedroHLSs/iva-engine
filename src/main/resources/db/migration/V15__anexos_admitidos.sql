-- R03 1.1.0 (30/09/2026): o catálogo passa a declarar, para cada cClassTrib,
-- os anexos em que o NCM pode estar, em vez de a regra aceitar qualquer anexo.
--
-- A coluna é anulável e nasce vazia. NULL quer dizer "a carga não declarou", e
-- a R03 responde NAO_AVALIADO para os códigos de benefício nessa situação;
-- 'NENHUM' quer dizer que o código não exige anexo; os demais valores são
-- identificadores de item_anexo separados por |. Nenhum INSERT nem UPDATE — o
-- valor só entra por importação de CSV (CLAUDE.md, seção 5).
--
-- ALTER TABLE não dispara os gatilhos de linha da V11, e uma carga selada não
-- tem nenhuma linha alterada por esta coluna nova.

alter table classificacao_tributaria
    add column anexos_admitidos text;
