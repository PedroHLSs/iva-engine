-- R04 1.1.0 (30/09/2026): o catálogo passa a declarar se o cClassTrib é de
-- tributação integral, em vez de a regra deduzir isso de "sem benefício e sem
-- redução informada".
--
-- A coluna é anulável e nasce vazia. NULL quer dizer "a carga não declarou",
-- nunca "não é integral": cargas gravadas antes desta migration ficam assim, e
-- a R04 responde NAO_AVALIADO para elas. Nenhum INSERT nem UPDATE — o valor só
-- entra por importação de CSV (CLAUDE.md, seção 5).
--
-- ALTER TABLE não dispara os gatilhos de linha da V11, e uma carga selada não
-- tem nenhuma linha alterada por esta coluna nova.

alter table classificacao_tributaria
    add column tributacao_integral boolean;
