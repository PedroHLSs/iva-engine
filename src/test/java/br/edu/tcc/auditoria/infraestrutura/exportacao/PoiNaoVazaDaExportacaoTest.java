package br.edu.tcc.auditoria.infraestrutura.exportacao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O formato do arquivo é detalhe de saída, e só {@code infraestrutura/exportacao}
 * pode conhecê-lo.
 *
 * <p>Mesma fronteira que {@code ClassesGeradasNaoVazamTest} guarda para o leiaute
 * da NF-e, pelo mesmo motivo: nada impede, hoje, que um caso de uso receba um
 * {@code Workbook} por parâmetro porque foi conveniente — e no dia em que isso
 * acontecer, trocar xlsx por CSV passa a mexer em regra de negócio.</p>
 *
 * <p>A aplicação monta {@code PapelDeTrabalho} e entrega à porta
 * {@code ExportadorDePapelDeTrabalho}. Quem quiser outro formato escreve outra
 * implementação, e não mexe em o que entra no relatório.</p>
 */
class PoiNaoVazaDaExportacaoTest {

    private static final Path RAIZ_DO_CODIGO =
            Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria");

    private static final Path FRONTEIRA_PERMITIDA =
            RAIZ_DO_CODIGO.resolve(Path.of("infraestrutura", "exportacao"));

    private static final String PACOTE_RESTRITO = "org.apache.poi";

    @Test
    void deveEncontrarCodigoForaDaFronteiraParaInspecionar() throws IOException {
        assertThat(arquivosForaDaFronteira()).isNotEmpty();
    }

    @Test
    void somenteInfraestruturaExportacaoPodeImportarPoi() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String linha = linhas.get(indice).strip();
                if (linha.startsWith("import ") && linha.contains(PACOTE_RESTRITO)) {
                    violacoes.add("%s:%d importa %s".formatted(
                            RAIZ_DO_CODIGO.relativize(arquivo), indice + 1, PACOTE_RESTRITO));
                }
            }
        }

        assertThat(violacoes)
                .as("Só \"%s\" pode conhecer o formato do arquivo. Quem precisar exportar fora dali "
                        + "usa a porta ExportadorDePapelDeTrabalho — ver D007 em "
                        + "docs/DECISOES-ARQUITETURA.md.", FRONTEIRA_PERMITIDA)
                .isEmpty();
    }

    private static List<Path> arquivosForaDaFronteira() throws IOException {
        try (Stream<Path> caminhos = Files.walk(RAIZ_DO_CODIGO)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(caminho -> caminho.getFileName().toString().endsWith(".java"))
                    .filter(caminho -> !caminho.startsWith(FRONTEIRA_PERMITIDA))
                    .toList();
        }
    }
}
