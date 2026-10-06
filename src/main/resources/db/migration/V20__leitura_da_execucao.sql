-- D018 (04/10/2026): marca de que a leitura de uma execução foi registrada.
--
-- Até aqui, "falha_de_leitura_da_execucao" só recebia linha pela análise da
-- interface web. O comando "auditar" da CLI imprimia os arquivos ilegíveis e não
-- os gravava, e a execução dele ficava sem linha nenhuma — indistinguível, no
-- banco, de uma execução em que nada falhou. A API e a planilha liam a tabela
-- vazia e afirmavam "nenhum arquivo deixou de ser lido".
--
-- Uma linha aqui afirma: a lista de ilegíveis desta execução foi gravada, e tem
-- "arquivos_ilegiveis" linhas em "falha_de_leitura_da_execucao". Sem linha aqui,
-- a leitura não foi registrada e quem lê diz isso, em vez de afirmar zero. A
-- marca é gravada na mesma transação das falhas.
--
-- TABELA VAZIA. Nenhum INSERT nem UPDATE: as execuções anteriores a esta
-- migration não ganham marca. As da interface web que deixaram item ou falha
-- gravados são reconhecidas pelo código; as demais ficam "não registradas", que
-- é o que elas são.

create table leitura_da_execucao (
    execucao_id        uuid    not null,
    arquivos_ilegiveis integer not null,

    constraint leitura_da_execucao_pk primary key (execucao_id),
    constraint leitura_da_execucao_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint leitura_da_execucao_contagem check (arquivos_ilegiveis >= 0)
);
