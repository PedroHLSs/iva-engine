-- R05 1.3.0 (03/10/2026, D017): o catálogo passa a declarar, para cada
-- cClassTrib, se a redução incide sobre a alíquota ou sobre a base de cálculo.
-- Até aqui a R05 deduzia a redução de base de dois CST escritos no código dela,
-- o que contrariava o CLAUDE.md, seção 5.
--
-- A coluna é anulável e nasce vazia. NULL quer dizer "a carga não declarou", e
-- a R05 lê a redução como de alíquota, como a decisão D1 do usuário definiu para
-- a coluna de redução. A restrição abaixo só confere a forma; qual código tem
-- qual incidência entra por importação de CSV (seção 5). Nenhum INSERT nem
-- UPDATE.
--
-- ALTER TABLE não dispara os gatilhos de linha da V11, e uma carga selada não
-- tem nenhuma linha alterada por esta coluna nova.

alter table classificacao_tributaria
    add column reducao_incide_sobre text,
    add constraint classificacao_tributaria_reducao_incide_sobre_forma
        check (reducao_incide_sobre in ('ALIQUOTA', 'BASE'));
