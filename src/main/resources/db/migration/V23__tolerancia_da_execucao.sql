-- D023 (04/10/2026): a tolerância de valor da R05 que a execução usou, e de
-- onde ela veio.
--
-- Duas execuções com tolerâncias diferentes produzem resultados diferentes na
-- R05, e nada registrava qual valor tinha sido usado. As colunas são anuláveis
-- e nascem vazias; NULL é "não registrada" — a execução é anterior a esta
-- migration —, e as duas são nulas juntas. A origem é PADRAO, quando a
-- instalação não configurou a tolerância e valeu o padrão declarado no
-- application.properties, ou CONFIGURADA. Nenhum INSERT nem UPDATE.

alter table execucao_auditoria
    add column tolerancia_de_valor numeric,
    add column origem_da_tolerancia text,
    add constraint execucao_auditoria_tolerancia_nao_negativa check (tolerancia_de_valor >= 0),
    add constraint execucao_auditoria_origem_da_tolerancia
        check (origem_da_tolerancia in ('PADRAO', 'CONFIGURADA')),
    add constraint execucao_auditoria_tolerancia_junta
        check ((tolerancia_de_valor is null) = (origem_da_tolerancia is null));
