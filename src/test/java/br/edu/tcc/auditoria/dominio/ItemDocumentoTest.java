package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ItemDocumentoInvalido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemDocumentoTest {

    @Test
    void deveConstruirItemSemNenhumCampoDeIbsCbs() {
        ItemDocumento item = DadosFicticios.itemSemCamposDeIbsCbs();

        assertThat(item.numeroItem()).isEqualTo(1);
        assertThat(item.semNenhumCampoDeIbsCbs()).isTrue();
    }

    @Test
    void deveAceitarItemSemNcmESemCfop() {
        // O sistema audita documentos já emitidos: precisa conseguir representar
        // inclusive o item incompleto, sob pena de não ter o que apontar.
        ItemDocumento item = itemBase(Optional.empty(), Optional.empty(), new BigDecimal("99.99"));

        assertThat(item.ncm()).isEmpty();
        assertThat(item.cfop()).isEmpty();
    }

    @Test
    void deveRejeitarNumeroDeItemZero() {
        assertThatThrownBy(() -> itemComNumero(0)).isInstanceOf(ItemDocumentoInvalido.class);
    }

    @Test
    void deveRejeitarNumeroDeItemNegativo() {
        assertThatThrownBy(() -> itemComNumero(-1)).isInstanceOf(ItemDocumentoInvalido.class);
    }

    @Test
    void deveRejeitarValorDoItemNulo() {
        assertThatThrownBy(() -> itemBase(Optional.empty(), Optional.empty(), null))
                .isInstanceOf(ItemDocumentoInvalido.class);
    }

    @Test
    void deveRejeitarNcmNuloEmVezDeOptionalVazio() {
        assertThatThrownBy(() -> itemBase(null, Optional.empty(), new BigDecimal("99.99")))
                .isInstanceOf(ItemDocumentoInvalido.class)
                .hasMessageContaining("ncm");
    }

    @Test
    void deveAceitarValorNegativoSemJulgar() {
        // Validar faixa aqui impediria de representar — e portanto de apontar — o
        // que o documento realmente declarou.
        ItemDocumento item = itemBase(Optional.empty(), Optional.empty(), new BigDecimal("-99.99"));

        assertThat(item.valorItem()).isEqualByComparingTo("-99.99");
    }

    @Test
    void deveConsiderarIguaisDoisItensComOsMesmosCampos() {
        assertThat(DadosFicticios.itemSemCamposDeIbsCbs())
                .isEqualTo(DadosFicticios.itemSemCamposDeIbsCbs());
    }

    @Test
    void naoDeveEstarSemCamposDeIbsCbsQuandoAoMenosUmFoiInformado() {
        ItemDocumento item = new ItemDocumento(
                1,
                Optional.empty(),
                Optional.empty(),
                new BigDecimal("99.99"),
                Optional.of(new CodigoCst(DadosFicticios.CST)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertThat(item.semNenhumCampoDeIbsCbs()).isFalse();
    }

    private static ItemDocumento itemComNumero(int numeroItem) {
        return new ItemDocumento(
                numeroItem,
                Optional.empty(),
                Optional.empty(),
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
                Optional.empty());
    }

    private static ItemDocumento itemBase(Optional<Ncm> ncm, Optional<Cfop> cfop, BigDecimal valorItem) {
        return new ItemDocumento(
                1,
                ncm,
                cfop,
                valorItem,
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
                Optional.empty());
    }
}
