package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A data em dd/mm/aaaa, aceita desde 14/09/2026 ao lado de aaaa-mm-dd.
 *
 * <p>O formato antigo continua coberto por {@code LeitorCsvTest}; aqui fica o
 * que a emenda acrescentou, e principalmente o que ela não pode acrescentar:
 * data inexistente ajustada em silêncio.</p>
 */
class DataEmDiaMesAnoNoCsvTest {

    @Test
    void deveAceitarDataObrigatoriaNoFormatoDiaMesAno() throws IOException {
        LinhaCsv linha = primeiraLinha("31/12/1900");

        assertThat(linha.dataObrigatoria("data")).isEqualTo(LocalDate.of(1900, 12, 31));
    }

    @Test
    void deveAceitarDataOpcionalNoFormatoDiaMesAno() throws IOException {
        LinhaCsv linha = primeiraLinha("31/12/1900");

        assertThat(linha.data("data")).contains(LocalDate.of(1900, 12, 31));
    }

    @Test
    void deveContinuarAceitandoDataNoFormatoAnoMesDia() throws IOException {
        LinhaCsv linha = primeiraLinha("1900-12-31");

        assertThat(linha.dataObrigatoria("data")).isEqualTo(LocalDate.of(1900, 12, 31));
    }

    @Test
    void deveRecusarDiaQueNaoExisteEmVezDeAjustarParaOFimDoMes() throws IOException {
        LinhaCsv linha = primeiraLinha("31/02/1900");

        assertThatThrownBy(() -> linha.dataObrigatoria("data"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("31/02/1900");
    }

    @Test
    void deveRecusarAnoComDoisDigitos() throws IOException {
        LinhaCsv linha = primeiraLinha("31/12/00");

        assertThatThrownBy(() -> linha.dataObrigatoria("data"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class);
    }

    @Test
    void deveRecusarDiaEMesSemOZeroAEsquerda() throws IOException {
        LinhaCsv linha = primeiraLinha("1/2/1900");

        assertThatThrownBy(() -> linha.dataObrigatoria("data"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class);
    }

    @Test
    void deveCitarOsDoisFormatosAceitosAoRecusar() throws IOException {
        LinhaCsv linha = primeiraLinha("1900/12/31");

        assertThatThrownBy(() -> linha.dataObrigatoria("data"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("aaaa-mm-dd")
                .hasMessageContaining("dd/mm/aaaa");
    }

    private static LinhaCsv primeiraLinha(String valorDaData) throws IOException {
        return LeitorCsv.ler(
                        ArquivoDeTeste.conteudo("data\n" + valorDaData + "\n"),
                        ImportacaoDeCatalogoInvalida::new)
                .get(0);
    }
}
