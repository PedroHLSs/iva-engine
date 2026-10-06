-- R07 1.1.0 (03/10/2026, D015): o item passa a guardar os seis campos do grupo
-- gRed do leiaute — pRedAliq e pAliqEfet de gIBSUF, gIBSMun e gCBS —, que até
-- aqui não eram lidos. Sem eles a R07 não tinha como cobrar o grupo de redução,
-- nem com o catálogo exigindo.
--
-- Colunas anuláveis, numeric sem precisão declarada, como as outras de valor do
-- item: NULL quer dizer "não veio", e a escala declarada no XML é preservada.
-- Item gravado antes desta migration fica com as seis nulas. Nenhum INSERT nem
-- UPDATE.

alter table item_documento
    add column reducao_aliquota_ibs_uf        numeric,
    add column aliquota_efetiva_ibs_uf        numeric,
    add column reducao_aliquota_ibs_municipal numeric,
    add column aliquota_efetiva_ibs_municipal numeric,
    add column reducao_aliquota_cbs           numeric,
    add column aliquota_efetiva_cbs           numeric;
