-- ---------------------------------------------------------------------------
-- Vínculo entre uma análise e a que ela corrige (Etapa 12, D013).
--
-- TABELA VAZIA. Nenhum INSERT.
--
-- Dado já auditado não é editável. Quando quem analisou percebe que enviou o
-- arquivo errado, ou corrige o XML na origem, o caminho é uma análise NOVA —
-- com o próprio hash_entrada, correspondendo ao que ela de fato processou — e
-- esta tabela registra que ela corrige a anterior. A anterior não muda: continua
-- com o hash dela, a carga dela e os apontamentos dela.
--
-- Tabela à parte, e não coluna em "execucao_auditoria", para não mexer na
-- linha da execução, que é registro de auditoria e não tem caminho de escrita
-- depois de gravada.
-- ---------------------------------------------------------------------------

create table correcao_de_analise (
    execucao_id          uuid        not null,
    corrige_execucao_id  uuid        not null,
    registrado_em        timestamptz not null,
    registrado_por       uuid        not null,

    constraint correcao_de_analise_pk primary key (execucao_id),
    constraint correcao_de_analise_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint correcao_de_analise_anterior_fk foreign key (corrige_execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint correcao_de_analise_autor_fk foreign key (registrado_por)
        references usuario (id) on delete restrict,
    constraint correcao_de_analise_nao_corrige_a_si
        check (execucao_id <> corrige_execucao_id)
);

create index correcao_de_analise_por_anterior on correcao_de_analise (corrige_execucao_id);
