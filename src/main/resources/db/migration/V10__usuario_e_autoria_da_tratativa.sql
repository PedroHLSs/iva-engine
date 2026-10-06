-- ---------------------------------------------------------------------------
-- Usuários e autoria da tratativa (Etapa 12, D013).
--
-- NENHUM USUÁRIO NASCE AQUI. Não há INSERT em "usuario": nem administrador
-- padrão, nem senha padrão. O primeiro administrador é criado pelo comando
-- "criar-administrador", na máquina onde o sistema roda, com a senha digitada
-- no console. Uma senha escrita numa migration seria a mesma em toda instalação.
--
-- A SENHA NUNCA É GRAVADA. "hash_senha" guarda o resultado do BCrypt, que já
-- traz o próprio sal e o custo dentro do texto. A restrição de formato abaixo é
-- a barreira de última instância contra alguém gravar a senha em texto claro
-- nesta coluna por engano.
-- ---------------------------------------------------------------------------

create table usuario (
    id            uuid        not null,
    login         text        not null,
    nome          text        not null,
    hash_senha    text        not null,
    perfil        text        not null,
    ativo         boolean     not null,
    criado_em     timestamptz not null,
    desativado_em timestamptz,

    constraint usuario_pk primary key (id),
    constraint usuario_login_unico unique (login),
    constraint usuario_login_preenchido check (btrim(login) <> ''),
    constraint usuario_nome_preenchido check (btrim(nome) <> ''),
    constraint usuario_perfil_conhecido
        check (perfil in ('ADMINISTRADOR', 'FISCAL', 'CONSULTA')),
    constraint usuario_hash_bcrypt check (hash_senha ~ '^\$2[aby]?\$[0-9]{2}\$.{53}$'),
    constraint usuario_desativacao_coerente
        check ((ativo and desativado_em is null) or (not ativo and desativado_em is not null))
);

-- ---------------------------------------------------------------------------
-- Histórico da tratativa: uma linha por decisão registrada, e nenhuma apagada.
--
-- POR QUE ISTO EXISTE AO LADO DE "tratativa"
--
-- A tabela "tratativa" (V4) guarda a decisão que vale hoje, e é sobrescrita
-- quando alguém trata de novo: vale a última. Isso estava certo enquanto
-- ninguém era identificado. Com autoria, sobrescrever apagaria quem decidiu
-- antes, e a tratativa é juízo humano registrado: precisa continuar atribuída a
-- alguém identificável mesmo depois de outra pessoa decidir diferente.
--
-- Esta tabela só recebe INSERT. O gatilho no fim recusa UPDATE e DELETE.
--
-- AUTOR AUSENTE É ESTADO ESCRITO, NÃO NULO MUDO
--
-- Tratativas gravadas antes desta etapa não têm autor, e isso NÃO é lido como
-- "alguém". O autor fica nulo com o motivo ao lado — o mesmo par
-- valor/motivo usado em todo o projeto (D002).
--
-- ON DELETE RESTRICT é a segunda barreira da regra "quem já decidiu é
-- desativado, não removido": mesmo que o código errasse, o banco recusa.
-- ---------------------------------------------------------------------------

create table tratativa_registro (
    id                       uuid        not null,
    hash_item                varchar(64) not null,
    regra_id                 text        not null,
    regra_versao             text        not null,
    decisao                  text        not null,
    justificativa            text        not null,
    registrado_em            timestamptz not null,
    autor_id                 uuid,
    motivo_do_autor_ausente  text,

    constraint tratativa_registro_pk primary key (id),
    constraint tratativa_registro_autor_fk foreign key (autor_id)
        references usuario (id) on delete restrict,
    constraint tratativa_registro_hash_hexadecimal check (hash_item ~ '^[0-9a-f]{64}$'),
    constraint tratativa_registro_decisao_conhecida check (decisao in ('ACEITO', 'REFUTADO')),
    constraint tratativa_registro_justificativa_preenchida check (btrim(justificativa) <> ''),
    constraint tratativa_registro_autor_explicado
        check ((autor_id is not null and motivo_do_autor_ausente is null)
               or (autor_id is null and motivo_do_autor_ausente is not null
                   and btrim(motivo_do_autor_ausente) <> ''))
);

create index tratativa_registro_por_chave
    on tratativa_registro (hash_item, regra_id, regra_versao, registrado_em);
create index tratativa_registro_por_autor on tratativa_registro (autor_id);

-- As tratativas que já existiam entram no histórico sem autor, com o motivo.
-- Não é dado normativo: é a cópia do que a tabela "tratativa" já guardava, para
-- o histórico começar completo em vez de começar nesta etapa.
insert into tratativa_registro
    (id, hash_item, regra_id, regra_versao, decisao, justificativa, registrado_em,
     autor_id, motivo_do_autor_ausente)
select id, hash_item, regra_id, regra_versao, decisao, justificativa, registrado_em,
       null,
       'registrada antes de o sistema identificar usuários (Etapa 12); não há como saber quem decidiu'
from tratativa;

create function recusar_alteracao_do_historico_de_tratativa() returns trigger as $$
begin
    raise exception 'O histórico de tratativa só recebe acréscimo: % recusado. A decisão registrada é juízo humano, e alterá-la ou apagá-la apagaria quem decidiu.', tg_op;
end;
$$ language plpgsql;

create trigger tratativa_registro_so_acrescimo
    before update or delete on tratativa_registro
    for each row execute function recusar_alteracao_do_historico_de_tratativa();
