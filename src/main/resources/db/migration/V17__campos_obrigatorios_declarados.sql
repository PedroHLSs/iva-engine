-- R07 1.1.0 (03/10/2026, D015): a carga passa a dizer se declarou os campos
-- exigidos por cClassTrib. Até aqui a tabela classificacao_tributaria_campo_obrigatorio
-- era a única fonte, e "célula em branco" e "nenhum campo exigido" ficavam
-- iguais: as duas sem linha. A R07 lia as duas como "nenhum campo exigido" e
-- respondia CONFORME sem ter o que conferir.
--
-- A coluna é anulável e nasce vazia. TRUE quer dizer que a carga declarou —
-- uma lista de nomes, ou NENHUM; FALSE quer dizer célula em branco, "não
-- declarado"; NULL é carga gravada antes desta migration. Nenhum INSERT nem
-- UPDATE: o valor só entra por importação de CSV (CLAUDE.md, seção 5).
--
-- ALTER TABLE não dispara os gatilhos de linha da V11, e uma carga selada não
-- tem nenhuma linha alterada por esta coluna nova.

alter table classificacao_tributaria
    add column campos_obrigatorios_declarados boolean;
