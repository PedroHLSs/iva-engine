-- ---------------------------------------------------------------------------
-- O que cada execução não conseguiu julgar, e quais apontamentos ela produziu.
--
-- Estas duas tabelas existem para que o papel de trabalho de uma execução possa
-- ser reemitido meses depois exatamente como era (D007).
--
-- DIFERENÇA DE NATUREZA ENTRE AS DUAS
--
-- Um apontamento é um fato sobre um documento: o mesmo item, sob a mesma regra
-- e na mesma versão dela, é sempre o mesmo apontamento, e por isso a tabela
-- "achado" não duplica ao reprocessar. Já uma avaliação não concluída é um fato
-- sobre a RODADA: "nesta execução, com este catálogo, esta regra não teve como
-- julgar este item". Rodar de novo com um catálogo mais completo produz outro
-- resultado, e os dois precisam continuar existindo lado a lado.
--
-- Por isso "avaliacao_nao_concluida" é escopada por execução e não deduplica, e
-- por isso "achado_da_execucao" existe: sem ela, saber quais apontamentos uma
-- execução antiga produziu seria impossível, porque a linha do apontamento só
-- guarda a primeira e a última execução que o viram.
-- ---------------------------------------------------------------------------

create table avaliacao_nao_concluida (
    id           uuid        not null,
    execucao_id  uuid        not null,
    chave_acesso varchar(44) not null,
    numero_item  integer     not null,
    regra_id     text        not null,
    regra_versao text        not null,
    -- Por que a regra não concluiu, no texto que a própria regra escreveu.
    -- Guardar só a contagem faria o relatório dizer "trinta não avaliadas" sem
    -- dizer se faltou campo no documento ou tabela no catálogo.
    motivo       text        not null,

    constraint avaliacao_nao_concluida_pk primary key (id),
    constraint avaliacao_nao_concluida_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint avaliacao_nao_concluida_unica unique (execucao_id, chave_acesso, numero_item, regra_id),
    constraint avaliacao_nao_concluida_numero_valido check (numero_item >= 1),
    constraint avaliacao_nao_concluida_chave_com_44_digitos check (chave_acesso ~ '^[0-9]{44}$'),
    constraint avaliacao_nao_concluida_motivo_preenchido check (btrim(motivo) <> '')
);

create index avaliacao_nao_concluida_por_execucao on avaliacao_nao_concluida (execucao_id);

-- Quais apontamentos cada execução produziu.
--
-- Sem chave estrangeira para "documento": o apontamento já a tem.
create table achado_da_execucao (
    execucao_id uuid not null,
    achado_id   uuid not null,

    constraint achado_da_execucao_pk primary key (execucao_id, achado_id),
    constraint achado_da_execucao_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint achado_da_execucao_achado_fk foreign key (achado_id)
        references achado (id) on delete cascade
);

create index achado_da_execucao_por_achado on achado_da_execucao (achado_id);
