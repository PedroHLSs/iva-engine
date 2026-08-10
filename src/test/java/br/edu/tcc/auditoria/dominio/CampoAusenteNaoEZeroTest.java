package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ItemDocumentoInvalido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decisão central do modelo: campo não informado e campo informado como zero
 * são estados distintos, e o domínio precisa mantê-los distinguíveis do começo
 * ao fim.
 *
 * <p>Um item que não declarou base de cálculo de IBS omitiu informação. Um item
 * que declarou base zero prestou informação. As regras de auditoria produzem
 * apontamentos diferentes para os dois casos, então qualquer conversão de
 * ausência em zero — por conveniência de leitura de XML, de mapeamento de banco
 * ou de assinatura de método — apaga o que o sistema existe para detectar.</p>
 */
class CampoAusenteNaoEZeroTest {

    private static final BigDecimal ZERO_COM_DUAS_CASAS = new BigDecimal("0.00");

    @Test
    void deveDistinguirCampoAusenteDeCampoInformadoComoZero() {
        ItemDocumento informadoComoZero = itemComBaseCalculoIbs(Optional.of(ZERO_COM_DUAS_CASAS));
        ItemDocumento naoInformado = itemComBaseCalculoIbs(Optional.empty());

        assertThat(informadoComoZero.baseCalculoIbs()).isPresent();
        assertThat(naoInformado.baseCalculoIbs()).isEmpty();
        assertThat(informadoComoZero).isNotEqualTo(naoInformado);
    }

    @Test
    void campoAusenteNaoDeveDevolverZero() {
        ItemDocumento naoInformado = itemComBaseCalculoIbs(Optional.empty());

        assertThat(naoInformado.baseCalculoIbs().orElse(null)).isNull();
        assertThat(naoInformado.baseCalculoIbs()).isNotEqualTo(Optional.of(BigDecimal.ZERO));
        assertThat(naoInformado.baseCalculoIbs()).isNotEqualTo(Optional.of(ZERO_COM_DUAS_CASAS));
    }

    @Test
    void campoInformadoComoZeroDeveDevolverZero() {
        ItemDocumento informadoComoZero = itemComBaseCalculoIbs(Optional.of(ZERO_COM_DUAS_CASAS));

        assertThat(informadoComoZero.baseCalculoIbs()).isPresent();
        assertThat(informadoComoZero.baseCalculoIbs().orElseThrow()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deveDistinguirTodosOsCamposDeIbsCbsZeradosDeTodosAusentes() {
        ItemDocumento tudoZerado = itemComTodosOsCamposDeIbsCbs(Optional.of(ZERO_COM_DUAS_CASAS));
        ItemDocumento tudoAusente = itemComTodosOsCamposDeIbsCbs(Optional.empty());

        assertThat(tudoZerado.semNenhumCampoDeIbsCbs()).isFalse();
        assertThat(tudoAusente.semNenhumCampoDeIbsCbs()).isTrue();
        assertThat(tudoZerado).isNotEqualTo(tudoAusente);
    }

    @Test
    void deveRecusarNuloComoFormaDeDizerQueOCampoNaoVeio() {
        // Ausência tem uma única grafia no domínio: Optional.empty().
        assertThatThrownBy(() -> itemComBaseCalculoIbs(null))
                .isInstanceOf(ItemDocumentoInvalido.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void deveRecusarCodigoVazioComoFormaDeDizerQueOCampoNaoVeio() {
        // O mesmo vale para códigos: ausência não é texto em branco.
        assertThatThrownBy(() -> new CodigoCst(""))
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void deveDistinguirAliquotaAusenteDeAliquotaZerada() {
        ItemDocumento zerada = itemComAliquotaCbs(Optional.of(ZERO_COM_DUAS_CASAS));
        ItemDocumento ausente = itemComAliquotaCbs(Optional.empty());

        assertThat(zerada.aliquotaCbs()).isPresent();
        assertThat(ausente.aliquotaCbs()).isEmpty();
        assertThat(zerada).isNotEqualTo(ausente);
    }

    @Test
    void deveDistinguirValorDeTributoAusenteDeValorZerado() {
        ItemDocumento zerado = itemComValorCbs(Optional.of(ZERO_COM_DUAS_CASAS));
        ItemDocumento ausente = itemComValorCbs(Optional.empty());

        assertThat(zerado.valorCbs()).isPresent();
        assertThat(ausente.valorCbs()).isEmpty();
        assertThat(zerado).isNotEqualTo(ausente);
    }

    @Test
    void devePreservarAEscalaDeclaradaDoValor() {
        // Para a auditoria, "0" e "0,00" são registros diferentes do mesmo número:
        // a escala declarada é informação, e comparação de grandeza usa compareTo.
        ItemDocumento comDuasCasas = itemComBaseCalculoIbs(Optional.of(new BigDecimal("0.00")));
        ItemDocumento semCasas = itemComBaseCalculoIbs(Optional.of(new BigDecimal("0")));

        assertThat(comDuasCasas.baseCalculoIbs().orElseThrow().scale()).isEqualTo(2);
        assertThat(semCasas.baseCalculoIbs().orElseThrow().scale()).isZero();
        assertThat(comDuasCasas.baseCalculoIbs().orElseThrow())
                .isEqualByComparingTo(semCasas.baseCalculoIbs().orElseThrow());
    }

    private static ItemDocumento itemComBaseCalculoIbs(Optional<BigDecimal> baseCalculoIbs) {
        return new ItemDocumento(
                1,
                Optional.of(new Ncm(DadosFicticios.NCM)),
                Optional.of(new Cfop(DadosFicticios.CFOP)),
                new BigDecimal("99.99"),
                Optional.of(new CodigoCst(DadosFicticios.CST)),
                Optional.of(new CodigoCst(DadosFicticios.CST)),
                Optional.of(new CodigoClassificacaoTributaria(DadosFicticios.CLASSIFICACAO_TRIBUTARIA)),
                baseCalculoIbs,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ItemDocumento itemComAliquotaCbs(Optional<BigDecimal> aliquotaCbs) {
        return new ItemDocumento(
                1,
                Optional.of(new Ncm(DadosFicticios.NCM)),
                Optional.of(new Cfop(DadosFicticios.CFOP)),
                new BigDecimal("99.99"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                aliquotaCbs,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ItemDocumento itemComValorCbs(Optional<BigDecimal> valorCbs) {
        return new ItemDocumento(
                1,
                Optional.of(new Ncm(DadosFicticios.NCM)),
                Optional.of(new Cfop(DadosFicticios.CFOP)),
                new BigDecimal("99.99"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                valorCbs);
    }

    private static ItemDocumento itemComTodosOsCamposDeIbsCbs(Optional<BigDecimal> valor) {
        Optional<CodigoCst> cst = valor.map(qualquer -> new CodigoCst(DadosFicticios.CST));
        Optional<CodigoClassificacaoTributaria> classificacao =
                valor.map(qualquer -> new CodigoClassificacaoTributaria(DadosFicticios.CLASSIFICACAO_TRIBUTARIA));

        return new ItemDocumento(
                1,
                Optional.of(new Ncm(DadosFicticios.NCM)),
                Optional.of(new Cfop(DadosFicticios.CFOP)),
                new BigDecimal("99.99"),
                cst,
                cst,
                classificacao,
                valor,
                valor,
                valor,
                valor,
                valor,
                valor,
                valor,
                valor);
    }
}
