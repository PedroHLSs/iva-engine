package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.AchadoInvalido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AchadoTest {

    @Test
    void deveConstruirAchadoDeItemComValorEmRisco() {
        Achado achado = DadosFicticios.achadoDeItem();

        assertThat(achado.ehDeItem()).isTrue();
        assertThat(achado.numeroItem()).hasValue(1);
        assertThat(achado.quantiaEmRisco()).isPresent();
        assertThat(achado.quantiaEmRisco().orElseThrow()).isEqualByComparingTo("99.99");
    }

    @Test
    void deveConstruirAchadoSemValorEmRiscoRegistrandoOMotivo() {
        Achado achado = achadoCom(
                OptionalInt.of(1),
                ValorEmRisco.naoCalculavel("incoerência de códigos, sem diferença monetária aferível"));

        assertThat(achado.quantiaEmRisco()).isEmpty();
        assertThat(achado.valorEmRisco().motivoDaAusencia())
                .contains("incoerência de códigos, sem diferença monetária aferível");
    }

    @Test
    void deveConstruirAchadoDeDocumentoSemNumeroDeItem() {
        Achado achado = achadoCom(OptionalInt.empty(), ValorEmRisco.naoCalculavel("apontamento do documento"));

        assertThat(achado.ehDeItem()).isFalse();
        assertThat(achado.numeroItem()).isEmpty();
    }

    @Test
    void deveRejeitarAchadoSemEvidencia() {
        assertThatThrownBy(() -> new Achado(
                DadosFicticios.REGRA_ID,
                DadosFicticios.REGRA_VERSAO,
                Severidade.GRAVE,
                DadosFicticios.chave(),
                OptionalInt.empty(),
                List.of(),
                DadosFicticios.FUNDAMENTO,
                DadosFicticios.vigencia(),
                ValorEmRisco.naoCalculavel("qualquer")))
                .isInstanceOf(AchadoInvalido.class)
                .hasMessageContaining("evidência");
    }

    @Test
    void deveRejeitarAchadoSemFundamentoNormativo() {
        assertThatThrownBy(() -> new Achado(
                DadosFicticios.REGRA_ID,
                DadosFicticios.REGRA_VERSAO,
                Severidade.GRAVE,
                DadosFicticios.chave(),
                OptionalInt.empty(),
                List.of(DadosFicticios.evidencia()),
                "  ",
                DadosFicticios.vigencia(),
                ValorEmRisco.naoCalculavel("qualquer")))
                .isInstanceOf(AchadoInvalido.class)
                .hasMessageContaining("fundamentoNormativo");
    }

    @Test
    void deveRejeitarAchadoSemVersaoDeRegra() {
        assertThatThrownBy(() -> new Achado(
                DadosFicticios.REGRA_ID,
                null,
                Severidade.GRAVE,
                DadosFicticios.chave(),
                OptionalInt.empty(),
                List.of(DadosFicticios.evidencia()),
                DadosFicticios.FUNDAMENTO,
                DadosFicticios.vigencia(),
                ValorEmRisco.naoCalculavel("qualquer")))
                .isInstanceOf(AchadoInvalido.class)
                .hasMessageContaining("regraVersao");
    }

    @Test
    void deveRejeitarNumeroDeItemNuloEmVezDeOptionalVazio() {
        assertThatThrownBy(() -> achadoCom(null, ValorEmRisco.naoCalculavel("qualquer")))
                .isInstanceOf(AchadoInvalido.class)
                .hasMessageContaining("OptionalInt.empty()");
    }

    @Test
    void deveRejeitarNumeroDeItemZero() {
        assertThatThrownBy(() -> achadoCom(OptionalInt.of(0), ValorEmRisco.naoCalculavel("qualquer")))
                .isInstanceOf(AchadoInvalido.class);
    }

    @Test
    void deveRejeitarListaDeEvidenciasComElementoNulo() {
        List<Evidencia> comNulo = Arrays.asList(DadosFicticios.evidencia(), null);

        assertThatThrownBy(() -> new Achado(
                DadosFicticios.REGRA_ID,
                DadosFicticios.REGRA_VERSAO,
                Severidade.GRAVE,
                DadosFicticios.chave(),
                OptionalInt.empty(),
                comNulo,
                DadosFicticios.FUNDAMENTO,
                DadosFicticios.vigencia(),
                ValorEmRisco.naoCalculavel("qualquer")))
                .isInstanceOf(AchadoInvalido.class);
    }

    @Test
    void deveCopiarAListaDeEvidenciasRecebida() {
        List<Evidencia> original = new ArrayList<>(List.of(DadosFicticios.evidencia()));

        Achado achado = new Achado(
                DadosFicticios.REGRA_ID,
                DadosFicticios.REGRA_VERSAO,
                Severidade.GRAVE,
                DadosFicticios.chave(),
                OptionalInt.empty(),
                original,
                DadosFicticios.FUNDAMENTO,
                DadosFicticios.vigencia(),
                ValorEmRisco.naoCalculavel("qualquer"));

        original.add(DadosFicticios.evidencia());

        assertThat(achado.evidencias()).hasSize(1);
    }

    @Test
    void naoDevePermitirAlterarAsEvidenciasDoAchado() {
        Achado achado = DadosFicticios.achadoDeItem();

        assertThatThrownBy(() -> achado.evidencias().add(DadosFicticios.evidencia()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Achado achadoCom(OptionalInt numeroItem, ValorEmRisco valorEmRisco) {
        return new Achado(
                DadosFicticios.REGRA_ID,
                DadosFicticios.REGRA_VERSAO,
                Severidade.MODERADA,
                DadosFicticios.chave(),
                numeroItem,
                List.of(DadosFicticios.evidencia()),
                DadosFicticios.FUNDAMENTO,
                DadosFicticios.vigencia(),
                valorEmRisco);
    }
}
