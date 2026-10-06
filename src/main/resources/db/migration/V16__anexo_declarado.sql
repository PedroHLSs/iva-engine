-- Cobertura por anexo da R03 1.1.0 (01/10/2026, decisões D6, D7 e D9 do
-- usuário). Cada carga pode declarar a lista dos anexos válidos e, para cada um,
-- o período em que os itens dele estão carregados.
--
-- Nasce vazia. Nenhum INSERT nem UPDATE: o conteúdo só entra por importação de
-- anexos-declarados.csv (CLAUDE.md, seção 5). Carga gravada antes desta migration
-- fica sem linha aqui, e a R03 responde NAO_AVALIADO para os códigos que citam
-- anexo, sem erro.
--
-- vigencia_inicio null: o anexo existe (vale para validar identificadores), mas
-- não está carregado em data nenhuma. Fim sem início é recusado.

create table anexo_declarado (
    carga_id        uuid not null,
    identificador   text not null,
    tipo_de_codigo  text not null,
    vigencia_inicio date,
    vigencia_fim    date,
    fonte_normativa text not null,

    constraint anexo_declarado_pk primary key (carga_id, identificador),
    constraint anexo_declarado_carga_fk foreign key (carga_id)
        references carga_catalogo (id) on delete cascade,
    constraint anexo_declarado_identificador_preenchido check (btrim(identificador) <> ''),
    constraint anexo_declarado_tipo_conhecido check (tipo_de_codigo in ('NCM', 'NBS', 'NCM_E_NBS')),
    constraint anexo_declarado_fim_exige_inicio check (vigencia_fim is null or vigencia_inicio is not null),
    constraint anexo_declarado_vigencia_coerente
        check (vigencia_fim is null or vigencia_fim >= vigencia_inicio),
    constraint anexo_declarado_fonte_preenchida check (btrim(fonte_normativa) <> '')
);

-- Carga selada não muda, como as demais tabelas da carga (V11).
create trigger anexo_declarado_carga_selada
    before insert or update or delete on anexo_declarado
    for each row execute function recusar_mudanca_em_carga_selada();
