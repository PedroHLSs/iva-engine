package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.OrigemDaTolerancia;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDaToleranciaDaExecucao;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

// Repositório que lê a tolerância de valor da R05 gravada com a execução (V23, D023). Execução anterior à V23 tem as duas colunas nulas, e a resposta é vazia: "não registrada", nunca um valor suposto.
@Repository
class ToleranciaDaExecucaoNoBanco implements ConsultaDaToleranciaDaExecucao {

    private final JdbcTemplate jdbc;

    // Construtor que recebe o acesso ao banco.
    ToleranciaDaExecucaoNoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ToleranciaDaExecucao> daExecucao(UUID execucaoId) {
        return jdbc.query("select tolerancia_de_valor, origem_da_tolerancia from execucao_auditoria where id = ?",
                (linha, numero) -> {
                    BigDecimal quantia = linha.getBigDecimal("tolerancia_de_valor");
                    String origem = linha.getString("origem_da_tolerancia");
                    return quantia == null || origem == null
                            ? Optional.<ToleranciaDaExecucao>empty()
                            : Optional.of(new ToleranciaDaExecucao(
                                    ToleranciaDeValor.de(quantia), OrigemDaTolerancia.valueOf(origem)));
                }, execucaoId).stream().findFirst().flatMap(lida -> lida);
    }
}
