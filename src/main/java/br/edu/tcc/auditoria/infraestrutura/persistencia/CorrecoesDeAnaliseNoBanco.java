package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.analise.CorrecoesDeAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.VinculosDaAnalise;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

// Repositório que grava e lê o vínculo entre uma análise e a que ela corrige, na tabela correcao_de_analise criada na V12. Acrescentado na Etapa 12.
@Repository
class CorrecoesDeAnaliseNoBanco implements CorrecoesDeAnalise {

    private final JdbcTemplate jdbc;

    // Construtor que recebe o acesso ao banco.
    CorrecoesDeAnaliseNoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeAnalise(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from execucao_auditoria where id = ?)", Boolean.class, id));
    }

    @Override
    @Transactional
    public void registrar(UUID nova, UUID anterior, UUID autor, Instant quando) {
        jdbc.update("insert into correcao_de_analise (execucao_id, corrige_execucao_id, registrado_em,"
                        + " registrado_por) values (?, ?, ?, ?)",
                nova, anterior, Timestamp.from(quando), autor);
    }

    @Override
    @Transactional(readOnly = true)
    public VinculosDaAnalise vinculos(UUID analise) {
        return new VinculosDaAnalise(
                jdbc.queryForList("select corrige_execucao_id from correcao_de_analise where execucao_id = ?",
                        UUID.class, analise).stream().findFirst(),
                jdbc.queryForList("select execucao_id from correcao_de_analise where corrige_execucao_id = ?"
                        + " order by registrado_em", UUID.class, analise));
    }
}
