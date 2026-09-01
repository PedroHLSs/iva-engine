package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.aplicacao.acuracia.EnderecoDaAvaliacao;
import br.edu.tcc.auditoria.aplicacao.acuracia.MetricasDaRegra;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escrita do relatório em CSV.
 *
 * <p><strong>Todos os valores são fictícios.</strong> Regras "RX1" e "RX2",
 * chave de dois repetido, catálogo "catalogo-ficticio-0".</p>
 */
class EscritorDeRelatorioDeAcuraciaCsvTest {

    private static final String CHAVE = "2".repeat(44);

    private final EscritorDeRelatorioDeAcuraciaCsv escritor = new EscritorDeRelatorioDeAcuraciaCsv();

    @Test
    void deveGravarUmaLinhaPorRegraMaisAConsolidada(@TempDir Path pasta) throws IOException {
        Path destino = pasta.resolve("acuracia.csv");

        escritor.escrever(relatorio(), destino);

        List<String> dados = linhasDeDados(destino);
        assertThat(dados).hasSize(4);
        assertThat(dados.get(0)).isEqualTo(EscritorDeRelatorioDeAcuraciaCsv.CABECALHO);
        assertThat(dados.get(1)).startsWith("RX1;");
        assertThat(dados.get(2)).startsWith("RX2;");
        assertThat(dados.get(3)).startsWith("CONSOLIDADO;");
    }

    @Test
    void deveGravarAsMetricasComQuatroCasasESeparadorDecimalPonto(@TempDir Path pasta)
            throws IOException {

        Path destino = pasta.resolve("acuracia.csv");

        escritor.escrever(relatorio(), destino);

        // RX1: VP=3 FP=1 FN=2 VN=2 NAv=4 SemAv=1 -> avaliados 8, total 13.
        assertThat(linhasDeDados(destino).get(1)).isEqualTo(
                "RX1;3;1;2;2;8;4;1;13;0.7500;0.6000;0.6667;0.6154");
    }

    @Test
    void metricaIndefinidaDeveSerEscritaENuncaDeixadaEmBranco(@TempDir Path pasta) throws IOException {
        Path destino = pasta.resolve("acuracia.csv");

        escritor.escrever(relatorio(), destino);

        // RX2 não tem nenhuma linha de gabarito: as quatro métricas são indefinidas.
        assertThat(linhasDeDados(destino).get(2)).isEqualTo(
                "RX2;0;0;0;0;0;0;0;0;(indefinida);(indefinida);(indefinida);(indefinida)");
    }

    @Test
    void oCabecalhoDeComentarioDeveIdentificarARodada(@TempDir Path pasta) throws IOException {
        Path destino = pasta.resolve("acuracia.csv");

        escritor.escrever(relatorio(), destino);

        String conteudo = Files.readString(destino, StandardCharsets.UTF_8);
        assertThat(conteudo)
                .contains("# catálogo: catalogo-ficticio-0")
                .contains("# conjunto de regras: conjunto-ficticio-0")
                .contains("# documentos auditados: 2")
                .contains("# itens auditados: 9")
                .contains("# avaliações produzidas pelo motor: 30")
                .contains("# avaliações sem linha no gabarito: 17");
    }

    @Test
    void deveListarEmComentarioOsEnderecosQueOMotorNaoAvaliou(@TempDir Path pasta) throws IOException {
        Path destino = pasta.resolve("acuracia.csv");

        escritor.escrever(relatorio(), destino);

        assertThat(Files.readString(destino, StandardCharsets.UTF_8))
                .contains("# endereços do gabarito que o motor não avaliou:")
                .contains("#   documento %s, item 4, regra RX1".formatted(CHAVE));
    }

    @Test
    void deveCriarAsPastasQueFaltarem(@TempDir Path pasta) {
        Path destino = pasta.resolve("relatorios").resolve("agosto").resolve("acuracia.csv");

        escritor.escrever(relatorio(), destino);

        assertThat(destino).exists();
    }

    @Test
    void deveDeclararAExtensaoQueProduz() {
        assertThat(escritor.extensao()).isEqualTo("csv");
    }

    /** As linhas que não são comentário: cabeçalho de colunas e dados. */
    private static List<String> linhasDeDados(Path arquivo) throws IOException {
        return Files.readAllLines(arquivo, StandardCharsets.UTF_8).stream()
                .filter(linha -> !linha.startsWith("#") && !linha.isBlank())
                .toList();
    }

    private static RelatorioDeAcuracia relatorio() {
        return new RelatorioDeAcuracia(
                "catalogo-ficticio-0",
                "conjunto-ficticio-0",
                2,
                9,
                30,
                17,
                List.of(
                        new MetricasDaRegra("RX1", new ContagemDeAcuracia(3, 1, 2, 2, 4, 1)),
                        new MetricasDaRegra("RX2", ContagemDeAcuracia.nenhuma())),
                List.of(new EnderecoDaAvaliacao(new ChaveAcesso(CHAVE), 4, "RX1")));
    }
}
