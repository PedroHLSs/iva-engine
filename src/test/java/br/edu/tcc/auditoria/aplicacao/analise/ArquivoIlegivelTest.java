package br.edu.tcc.auditoria.aplicacao.analise;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A primeira das duas barreiras contra a chave de acesso voltar para o banco. */
class ArquivoIlegivelTest {

    private static final String CHAVE_FICTICIA = "9".repeat(44);

    @Test
    void deveRecusarOrigemComCorridaDeQuarentaEQuatroDigitos() {
        assertThatThrownBy(() -> new ArquivoIlegivel(
                CHAVE_FICTICIA + "-nfe.xml", "TipoFicticio", "motivo fictício"))
                .isInstanceOf(AnaliseInvalida.class)
                .hasMessageContaining("44 dígitos");
    }

    @Test
    void naoDeveRepetirNaMensagemOValorQueRecusou() {
        assertThatThrownBy(() -> new ArquivoIlegivel(
                CHAVE_FICTICIA + "-nfe.xml", "TipoFicticio", "motivo fictício"))
                .describedAs("citar a chave escreveria no log exatamente o que a recusa impede "
                        + "de gravar")
                .hasMessageNotContaining(CHAVE_FICTICIA);
    }

    @Test
    void deveAceitarOrigemJaLimpa() {
        assertThatCode(() -> new ArquivoIlegivel(
                "nota-corrompida.xml", "DocumentoFiscalIlegivel", "motivo fictício"))
                .doesNotThrowAnyException();
    }

    @Test
    void deveExigirOsTresCampos() {
        assertThatThrownBy(() -> new ArquivoIlegivel("  ", "Tipo", "motivo"))
                .isInstanceOf(AnaliseInvalida.class)
                .hasMessageContaining("origem");
        assertThatThrownBy(() -> new ArquivoIlegivel("nota.xml", null, "motivo"))
                .isInstanceOf(AnaliseInvalida.class)
                .hasMessageContaining("tipoDeErro");
        assertThatThrownBy(() -> new ArquivoIlegivel("nota.xml", "Tipo", ""))
                .isInstanceOf(AnaliseInvalida.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void deveAceitarNomeComMenosDeQuarentaEQuatroDigitos() {
        assertThat(new ArquivoIlegivel("9".repeat(43) + ".xml", "Tipo", "motivo").origem())
                .describedAs("43 dígitos não são chave de acesso, e recusar seria barrar nome legítimo")
                .isEqualTo("9".repeat(43) + ".xml");
    }
}
