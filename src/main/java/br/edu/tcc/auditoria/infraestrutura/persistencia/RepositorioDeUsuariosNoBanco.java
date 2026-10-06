package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.identidade.HashDeSenha;
import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.RepositorioDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Repositório que grava os usuários na tabela usuario, criada na V10. O hash da senha só é lido por hashDe; nenhuma outra consulta daqui o traz.
@Repository
class RepositorioDeUsuariosNoBanco implements RepositorioDeUsuarios {

    private static final String COLUNAS = "id, login, nome, perfil, ativo, criado_em, desativado_em";

    private static final RowMapper<Usuario> USUARIO = (linha, numero) -> new Usuario(
            linha.getObject("id", UUID.class),
            linha.getString("login"),
            linha.getString("nome"),
            Perfil.valueOf(linha.getString("perfil")),
            linha.getBoolean("ativo"),
            linha.getTimestamp("criado_em").toInstant(),
            Optional.ofNullable(linha.getTimestamp("desativado_em")).map(Timestamp::toInstant));

    private final JdbcTemplate jdbc;

    // Construtor que recebe o acesso ao banco.
    RepositorioDeUsuariosNoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> porId(UUID id) {
        return jdbc.query("select " + COLUNAS + " from usuario where id = ?", USUARIO, id)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> porLogin(String login) {
        return jdbc.query("select " + COLUNAS + " from usuario where login = ?", USUARIO, login)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> todos() {
        return jdbc.query("select " + COLUNAS + " from usuario order by login", USUARIO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HashDeSenha> hashDe(UUID id) {
        return jdbc.queryForList("select hash_senha from usuario where id = ?", String.class, id)
                .stream().findFirst().map(HashDeSenha::new);
    }

    @Override
    @Transactional
    public void inserir(Usuario usuario, HashDeSenha hash) {
        jdbc.update("insert into usuario (id, login, nome, hash_senha, perfil, ativo, criado_em, desativado_em)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                usuario.id(), usuario.login(), usuario.nome(), hash.valor(), usuario.perfil().name(),
                usuario.ativo(), Timestamp.from(usuario.criadoEm()),
                usuario.desativadoEm().map(Timestamp::from).orElse(null));
    }

    @Override
    @Transactional
    public void atualizar(Usuario usuario) {
        jdbc.update("update usuario set nome = ?, perfil = ?, ativo = ?, desativado_em = ? where id = ?",
                usuario.nome(), usuario.perfil().name(), usuario.ativo(),
                usuario.desativadoEm().map(Timestamp::from).orElse(null), usuario.id());
    }

    @Override
    @Transactional
    public void trocarHash(UUID id, HashDeSenha hash) {
        jdbc.update("update usuario set hash_senha = ? where id = ?", hash.valor(), id);
    }

    @Override
    @Transactional
    public void remover(UUID id) {
        jdbc.update("delete from usuario where id = ?", id);
    }

    @Override
    @Transactional(readOnly = true)
    public long administradoresAtivos() {
        Long quantidade = jdbc.queryForObject(
                "select count(*) from usuario where perfil = 'ADMINISTRADOR' and ativo", Long.class);
        return quantidade == null ? 0 : quantidade;
    }

    // Olha o histórico da tratativa, e não a tabela da decisão que vale hoje: quem foi sobrescrito por outra pessoa também registrou. Mudou na Etapa 13: passou a contar também quem executou análise pela web, que o banco protege com a mesma restrição.
    @Override
    @Transactional(readOnly = true)
    public boolean temRegistroAtribuido(UUID id) {
        Boolean tem = jdbc.queryForObject(
                "select exists (select 1 from tratativa_registro where autor_id = ?)"
                        + " or exists (select 1 from correcao_de_analise where registrado_por = ?)"
                        + " or exists (select 1 from autoria_da_execucao where usuario_id = ?)",
                Boolean.class, id, id, id);
        return Boolean.TRUE.equals(tem);
    }

    // Trava a tabela de usuários contra outra escrita até o fim da transação; leitura continua livre.
    @Override
    @Transactional
    public <T> T comUsuariosTravados(Supplier<T> operacao) {
        jdbc.execute("lock table usuario in share row exclusive mode");
        return operacao.get();
    }
}
