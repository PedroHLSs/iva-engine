-- D019 (04/10/2026): quantos documentos repetidos, com o mesmo conteúdo, a
-- leitura do lote descartou.
--
-- O caso comum é o -nfe.xml e o -procNFe.xml da mesma nota no mesmo lote. A
-- leitura passou a contar a nota uma vez só, e a cópia descartada precisa ser
-- reportada junto da execução, ao lado dos arquivos que não puderam ser lidos.
--
-- A coluna é anulável e nasce vazia. NULL é "não registrado": a marca de
-- leitura foi gravada antes desta contagem existir. Nenhum INSERT nem UPDATE.

alter table leitura_da_execucao
    add column documentos_duplicados integer,
    add constraint leitura_da_execucao_duplicados check (documentos_duplicados >= 0);
