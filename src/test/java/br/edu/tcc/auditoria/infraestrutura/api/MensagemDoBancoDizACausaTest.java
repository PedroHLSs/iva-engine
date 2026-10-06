package br.edu.tcc.auditoria.infraestrutura.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

// D019 (04/10/2026): a recusa do banco diz a causa real. Antes, toda recusa dizia "outra pessoa mudou o mesmo dado ao mesmo tempo. Recarregue e tente de novo" — inclusive a violação de restrição que um lote com documento repetido produzia, que nenhuma repetição resolve. Os textos do banco aqui são fictícios, no formato do PostgreSQL.
class MensagemDoBancoDizACausaTest {

    private final TratadorDeErrosDaIdentidade tratador = new TratadorDeErrosDaIdentidade();

    // Violação de restrição: nomeia a restrição, diz que repetir não resolve, e não repassa o detalhe, que traz o valor gravado.
    @Test
    void violacaoDeRestricaoDeveNomearARestricaoSemMandarTentarDeNovo() {
        SQLException doBanco = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"item_da_execucao_unico\"\n"
                        + "  Detail: Key (chave_acesso)=(VALOR-FICTICIO-999) already exists.", "23505");

        String mensagem = mensagemDe(new DataIntegrityViolationException("could not execute statement", doBanco));

        assertThat(mensagem)
                .contains("item_da_execucao_unico")
                .contains("repetir o mesmo pedido terá o mesmo resultado")
                .doesNotContain("tente de novo")
                .doesNotContain("outra pessoa")
                .doesNotContain("VALOR-FICTICIO-999");
    }

    // Gatilho do próprio sistema: o texto é do sistema, e é a causa; vai inteiro, sem o contexto do PL/pgSQL.
    @Test
    void recusaDeGatilhoDeveRepassarOTextoDoGatilho() {
        SQLException doBanco = new SQLException(
                "ERROR: A carga de catálogo \"carga-ficticia\" está selada e não pode ser excluída.\n"
                        + "  Where: PL/pgSQL function ficticia() line 9 at RAISE", "P0001");

        String mensagem = mensagemDe(new UncategorizedSQLException("delete", "delete from x", doBanco));

        assertThat(mensagem)
                .contains("está selada e não pode ser excluída")
                .doesNotContain("PL/pgSQL")
                .doesNotContain("tente de novo");
    }

    // Concorrência de verdade é o único caso em que tentar de novo é o conselho certo.
    @Test
    void concorrenciaDeveMandarRecarregarETentarDeNovo() {
        assertThat(mensagemDe(new OptimisticLockingFailureException("versão mudou")))
                .contains("tente de novo");
    }

    private String mensagemDe(org.springframework.dao.DataAccessException recusa) {
        ResponseEntity<ErroExposto> resposta = tratador.bancoRecusou(recusa);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        return resposta.getBody().mensagem();
    }
}
