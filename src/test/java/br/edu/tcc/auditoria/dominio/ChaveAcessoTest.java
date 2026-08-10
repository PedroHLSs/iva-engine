package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ChaveAcessoInvalida;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaveAcessoTest {

    @Test
    void deveAceitarChaveComQuarentaEQuatroDigitos() {
        assertThat(new ChaveAcesso(DadosFicticios.CHAVE).valor()).isEqualTo(DadosFicticios.CHAVE);
    }

    @Test
    void deveRejeitarChaveNula() {
        assertThatThrownBy(() -> new ChaveAcesso(null))
                .isInstanceOf(ChaveAcessoInvalida.class);
    }

    @Test
    void deveRejeitarChaveComMenosDeQuarentaEQuatroDigitos() {
        assertThatThrownBy(() -> new ChaveAcesso("9".repeat(43)))
                .isInstanceOf(ChaveAcessoInvalida.class)
                .hasMessageContaining("44");
    }

    @Test
    void deveRejeitarChaveComMaisDeQuarentaEQuatroDigitos() {
        assertThatThrownBy(() -> new ChaveAcesso("9".repeat(45)))
                .isInstanceOf(ChaveAcessoInvalida.class);
    }

    @Test
    void deveRejeitarChaveComCaractereNaoNumerico() {
        assertThatThrownBy(() -> new ChaveAcesso("9".repeat(43) + "X"))
                .isInstanceOf(ChaveAcessoInvalida.class)
                .hasMessageContaining("dígitos");
    }

    @Test
    void deveRejeitarChaveComEspacoEmVolta() {
        assertThatThrownBy(() -> new ChaveAcesso(" " + "9".repeat(43)))
                .isInstanceOf(ChaveAcessoInvalida.class);
    }

    @Test
    void naoDeveReproduzirAChaveRecusadaNaMensagemDeErro() {
        String chaveComLetra = "9".repeat(43) + "X";

        assertThatThrownBy(() -> new ChaveAcesso(chaveComLetra))
                .isInstanceOf(ChaveAcessoInvalida.class)
                .hasMessageNotContaining(chaveComLetra);
    }

    @Test
    void deveConsiderarIguaisDuasChavesComOMesmoValor() {
        assertThat(new ChaveAcesso(DadosFicticios.CHAVE)).isEqualTo(new ChaveAcesso(DadosFicticios.CHAVE));
    }
}
