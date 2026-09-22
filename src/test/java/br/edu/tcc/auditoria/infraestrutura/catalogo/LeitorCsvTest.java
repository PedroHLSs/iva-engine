package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorCsvTest {

    @Test
    void deveIgnorarComentariosELinhasEmBrancoMantendoANumeracaoFisica() throws IOException {
        String csv = """
                # comentario de topo

                coluna
                valorFicticio
                # comentario no meio
                outroValorFicticio
                """;

        List<LinhaCsv> linhas = ler(csv);

        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).numero()).isEqualTo(4);
        assertThat(linhas.get(1).numero()).isEqualTo(6);
    }

    @Test
    void deveTratarCampoEmBrancoComoAusente() throws IOException {
        List<LinhaCsv> linhas = ler("colunaA;colunaB\nvalorFicticio;   \n");

        assertThat(linhas.get(0).texto("colunaA")).contains("valorFicticio");
        assertThat(linhas.get(0).texto("colunaB")).isEmpty();
    }

    @Test
    void deveRecusarColunaQueNaoExisteNoCabecalho() throws IOException {
        List<LinhaCsv> linhas = ler("colunaA\nvalorFicticio\n");

        assertThatThrownBy(() -> linhas.get(0).texto("colunaInexistente"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("colunaInexistente");
    }

    @Test
    void deveRecusarLinhaComQuantidadeDeCamposDiferenteDoCabecalho() {
        assertThatThrownBy(() -> ler("colunaA;colunaB\nso-um-campo\n"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2");
    }

    @Test
    void deveRecusarCabecalhoComColunaRepetida() {
        assertThatThrownBy(() -> ler("colunaA;colunaA\nx;y\n"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("repete");
    }

    @Test
    void deveRecusarArquivoSemCabecalho() {
        assertThatThrownBy(() -> ler("# so comentario\n\n"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("cabeçalho");
    }

    @Test
    void deveLerListaSeparadaPorBarraVerticalDentroDeUmCampo() throws IOException {
        List<LinhaCsv> linhas = ler("colunaA\numFicticio|outroFicticio\n");

        assertThat(linhas.get(0).lista("colunaA")).containsExactly("umFicticio", "outroFicticio");
    }

    @Test
    void deveDevolverListaVaziaQuandoOCampoDeListaVemEmBranco() throws IOException {
        List<LinhaCsv> linhas = ler("colunaA;colunaB\n;valorFicticio\n");

        assertThat(linhas.get(0).lista("colunaA")).isEmpty();
        assertThat(linhas.get(0).texto("colunaB")).contains("valorFicticio");
    }

    @Test
    void deveRecusarDataForaDoFormatoIndicandoALinha() throws IOException {
        // Até 14/09/2026 o exemplo era 01/01/1900, que passou a ser formato aceito.
        List<LinhaCsv> linhas = ler("colunaA\n1900/01/01\n");

        assertThatThrownBy(() -> linhas.get(0).dataObrigatoria("colunaA"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("aaaa-mm-dd");
    }

    @Test
    void deveAceitarPontoOuVirgulaComoSeparadorDecimal() throws IOException {
        List<LinhaCsv> linhas = ler("comVirgula;comPonto\n99,99;99.99\n");

        assertThat(linhas.get(0).decimal("comVirgula").orElseThrow()).isEqualByComparingTo("99.99");
        assertThat(linhas.get(0).decimal("comPonto").orElseThrow()).isEqualByComparingTo("99.99");
    }

    @Test
    void deveRecusarNumeroMalformadoIndicandoALinha() throws IOException {
        List<LinhaCsv> linhas = ler("colunaA\nnao-e-numero\n");

        assertThatThrownBy(() -> linhas.get(0).decimal("colunaA"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2");
    }

    /** Lê um CSV de teste recusando como a importação de catálogo recusa. */
    private static List<LinhaCsv> ler(String conteudo) throws IOException {
        return LeitorCsv.ler(ArquivoDeTeste.conteudo(conteudo), ImportacaoDeCatalogoInvalida::new);
    }
}
