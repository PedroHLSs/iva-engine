-- ---------------------------------------------------------------------------
-- O histórico de análises: quem executou, e o resumo por estado para filtrar no
-- servidor (Etapa 13, D014).
--
-- TABELAS VAZIAS. Nenhum INSERT, nenhum dado normativo.
--
-- QUEM EXECUTOU
--
-- Só a análise enviada pela web tem executor: é a única entrada em que há uma
-- pessoa logada. Execução feita pela linha de comando ("auditar") e execução
-- anterior à Etapa 13 NÃO têm linha aqui, e ficam como "executor não
-- registrado", escrito. Isso é POR DESENHO, e não lacuna a preencher depois: não
-- se cria executor padrão, nem se atribui a CLI a um usuário, porque isso seria
-- afirmar quem executou sem saber.
--
-- ON DELETE RESTRICT no usuário: quem executou análise é desativado, e não
-- apagado, pelo mesmo motivo da tratativa.
-- ---------------------------------------------------------------------------

create table autoria_da_execucao (
    execucao_id    uuid        not null,
    usuario_id     uuid        not null,
    registrado_em  timestamptz not null,

    constraint autoria_da_execucao_pk primary key (execucao_id),
    constraint autoria_da_execucao_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint autoria_da_execucao_usuario_fk foreign key (usuario_id)
        references usuario (id) on delete restrict
);

create index autoria_da_execucao_por_usuario on autoria_da_execucao (usuario_id);

-- ---------------------------------------------------------------------------
-- RESUMO POR ESTADO
--
-- Quantos produtos da execução estão em cada um dos quatro estados, e a situação
-- mais grave presente. É cópia do que o MontadorDaConferencia calcula — a
-- tradução de desfecho em estado continua morando num lugar só, e esta tabela
-- não a repete em SQL. Existe para o histórico filtrar e paginar no servidor.
--
-- Os quatro números são guardados separados, e NUNCA somados: não há coluna de
-- total, nem de "conformes + não avaliados".
--
-- "situacao_mais_grave" é a mais forte presente, pela precedência dos estados, e
-- não a da maioria: um lote com seis produtos sem divergência e quatro não
-- concluídos é "não foi possível concluir", e não "sem divergência".
--
-- Execução sem produtos listáveis (a da linha de comando, que não grava acervo)
-- tem situação nula COM o motivo ao lado.
-- ---------------------------------------------------------------------------

create table resumo_da_execucao (
    execucao_id                    uuid        not null,
    possivel_divergencia           integer     not null,
    requer_conferencia             integer     not null,
    nao_foi_possivel_concluir      integer     not null,
    sem_divergencia_identificada   integer     not null,
    produtos_com_pendencia         integer     not null,
    situacao_mais_grave            text,
    motivo_da_situacao_ausente     text,
    calculado_em                   timestamptz not null,

    constraint resumo_da_execucao_pk primary key (execucao_id),
    constraint resumo_da_execucao_execucao_fk foreign key (execucao_id)
        references execucao_auditoria (id) on delete cascade,
    constraint resumo_da_execucao_contagens_nao_negativas
        check (possivel_divergencia >= 0 and requer_conferencia >= 0
               and nao_foi_possivel_concluir >= 0 and sem_divergencia_identificada >= 0
               and produtos_com_pendencia >= 0),
    constraint resumo_da_execucao_situacao_conhecida
        check (situacao_mais_grave is null or situacao_mais_grave in
               ('POSSIVEL_DIVERGENCIA', 'REQUER_CONFERENCIA',
                'NAO_FOI_POSSIVEL_CONCLUIR', 'SEM_DIVERGENCIA_IDENTIFICADA')),
    constraint resumo_da_execucao_situacao_explicada
        check ((situacao_mais_grave is not null and motivo_da_situacao_ausente is null)
               or (situacao_mais_grave is null and motivo_da_situacao_ausente is not null))
);

create index resumo_da_execucao_por_situacao on resumo_da_execucao (situacao_mais_grave);
