package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.tratativa.AutorDaTratativa;
import br.edu.tcc.auditoria.aplicacao.tratativa.HistoricoDeTratativas;
import br.edu.tcc.auditoria.aplicacao.tratativa.RegistroDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.RepositorioTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Repositório que grava a tratativa com o autor: a decisão que vale hoje vai para a tabela tratativa, pelo mesmo repositório de sempre, e a linha do histórico vai para tratativa_registro, criada na V10, que só recebe acréscimo.
@Repository
class HistoricoDeTratativasNoBanco implements HistoricoDeTratativas {

    private final RepositorioTratativa tratativas;
    private final JdbcTemplate jdbc;

    private final RowMapper<RegistroDeTratativa> registro = (linha, numero) -> {
        Tratativa tratativa = new Tratativa(
                new ChaveDeTratativa(
                        new HashDoItem(linha.getString("hash_item")),
                        linha.getString("regra_id"),
                        linha.getString("regra_versao")),
                DecisaoDeTratativa.valueOf(linha.getString("decisao")),
                linha.getString("justificativa"),
                linha.getTimestamp("registrado_em").toInstant());
        UUID autorId = linha.getObject("autor_id", UUID.class);
        Optional<AutorDaTratativa> autor = autorId == null
                ? Optional.empty()
                : Optional.of(new AutorDaTratativa(autorId, linha.getString("login"),
                        linha.getString("nome"), linha.getBoolean("ativo")));
        return new RegistroDeTratativa(
                linha.getObject("id", UUID.class),
                tratativa,
                autor,
                Optional.ofNullable(linha.getString("motivo_do_autor_ausente")));
    };

    // Construtor que recebe o repositório de tratativas e o acesso ao banco.
    HistoricoDeTratativasNoBanco(RepositorioTratativa tratativas, JdbcTemplate jdbc) {
        this.tratativas = tratativas;
        this.jdbc = jdbc;
    }

    // Grava a decisão que passa a valer e a linha do histórico, na mesma transação.
    @Override
    @Transactional
    public RegistroDeTratativa gravar(Tratativa tratativa, UUID autorId) {
        tratativas.salvar(tratativa);

        UUID id = UUID.randomUUID();
        ChaveDeTratativa chave = tratativa.chave();
        jdbc.update("insert into tratativa_registro (id, hash_item, regra_id, regra_versao, decisao,"
                        + " justificativa, registrado_em, autor_id, motivo_do_autor_ausente)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, null)",
                id, chave.hashDoItem().valor(), chave.regraId(), chave.regraVersao(),
                tratativa.decisao().name(), tratativa.justificativa(),
                Timestamp.from(tratativa.registradoEm()), autorId);

        return jdbc.query(consulta("r.id = ?"), registro, id).get(0);
    }

    // Devolve o histórico da chave, do mais antigo ao mais recente.
    @Override
    @Transactional(readOnly = true)
    public List<RegistroDeTratativa> historico(ChaveDeTratativa chave) {
        return jdbc.query(
                consulta("r.hash_item = ? and r.regra_id = ? and r.regra_versao = ?")
                        + " order by r.registrado_em, r.id",
                registro, chave.hashDoItem().valor(), chave.regraId(), chave.regraVersao());
    }

    // Método auxiliar que monta a consulta do histórico com o autor, se houver.
    private static String consulta(String filtro) {
        return "select r.id, r.hash_item, r.regra_id, r.regra_versao, r.decisao, r.justificativa,"
                + " r.registrado_em, r.autor_id, r.motivo_do_autor_ausente, u.login, u.nome, u.ativo"
                + " from tratativa_registro r left join usuario u on u.id = r.autor_id"
                + " where " + filtro;
    }
}
