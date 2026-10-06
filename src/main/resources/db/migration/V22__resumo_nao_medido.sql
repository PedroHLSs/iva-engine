-- D020 (04/10/2026): o resumo de uma execução cujos produtos não foram medidos
-- fica sem contagem, e não com zeros.
--
-- O resumo da V13 conta produtos, e a lista de produtos sai de
-- "item_da_execucao". O comando auditar não grava essa tabela, e o resumo das
-- execuções dele era gravado com quatro zeros. Zero não era a contagem: era a
-- ausência dela, e o filtro por quantidade excluía execuções com apontamentos.
--
-- As cinco contagens passam a aceitar NULL, sempre as cinco juntas. NULL quer
-- dizer "não medido"; o motivo vai em motivo_da_situacao_ausente. Nenhum INSERT
-- nem UPDATE: as linhas já gravadas com zeros ficam como estão, e a consulta do
-- histórico não as lê como contagem quando a execução não registrou itens.

alter table resumo_da_execucao
    alter column possivel_divergencia drop not null,
    alter column requer_conferencia drop not null,
    alter column nao_foi_possivel_concluir drop not null,
    alter column sem_divergencia_identificada drop not null,
    alter column produtos_com_pendencia drop not null,
    add constraint resumo_da_execucao_contagens_juntas check (
        (possivel_divergencia is null and requer_conferencia is null and nao_foi_possivel_concluir is null
            and sem_divergencia_identificada is null and produtos_com_pendencia is null)
        or (possivel_divergencia is not null and requer_conferencia is not null
            and nao_foi_possivel_concluir is not null and sem_divergencia_identificada is not null
            and produtos_com_pendencia is not null));
