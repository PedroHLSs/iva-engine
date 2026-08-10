package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.IdentificadorPseudonimizadoInvalido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentificadorPseudonimizadoTest {

    @Test
    void deveAceitarResumoHexadecimalDeSessentaEQuatroCaracteres() {
        assertThat(new IdentificadorPseudonimizado(DadosFicticios.PSEUDONIMO_EMITENTE).valor())
                .isEqualTo(DadosFicticios.PSEUDONIMO_EMITENTE);
    }

    @Test
    void deveRejeitarIdentificadorNulo() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado(null))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarSequenciaDeQuatorzeDigitosNoFormatoDeCnpj() {
        // O ponto do tipo: um CNPJ não tem como entrar no domínio por engano.
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("00000000000000"))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarSequenciaDeOnzeDigitosNoFormatoDeCpf() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("00000000000"))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarCnpjFormatadoComPontuacao() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("00.000.000/0000-00"))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarTextoQueParecaRazaoSocial() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("EMPRESA FICTICIA LTDA"))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarTextoQueParecaEndereco() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("RUA FICTICIA, 999 - CENTRO"))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarHexadecimalComComprimentoErrado() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("a".repeat(63)))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("a".repeat(65)))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarHexadecimalEmMaiusculas() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("A".repeat(64)))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void deveRejeitarCaractereForaDoAlfabetoHexadecimal() {
        assertThatThrownBy(() -> new IdentificadorPseudonimizado("g".repeat(64)))
                .isInstanceOf(IdentificadorPseudonimizadoInvalido.class);
    }

    @Test
    void naoDeveReproduzirOValorRecusadoNaMensagemDeErro() {
        // Se o valor recusado for o dado em texto claro, a mensagem não pode vazá-lo.
        String possivelDadoPessoal = "EMPRESA FICTICIA LTDA";

        assertThatThrownBy(() -> new IdentificadorPseudonimizado(possivelDadoPessoal))
                .hasMessageNotContaining(possivelDadoPessoal);
    }

    @Test
    void deveDistinguirParticipantesComPseudonimosDiferentes() {
        assertThat(DadosFicticios.pseudonimoEmitente())
                .isNotEqualTo(DadosFicticios.pseudonimoDestinatario());
    }
}
