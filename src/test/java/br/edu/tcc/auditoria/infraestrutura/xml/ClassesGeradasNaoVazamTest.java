package br.edu.tcc.auditoria.infraestrutura.xml;

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
 * As classes geradas a partir do XSD são detalhe de infraestrutura, e só o
 * pacote {@code infraestrutura.xml} pode enxergá-las.
 *
 * <p>{@code DominioNaoDependeDeFrameworkTest} já barra o domínio. Falta a
 * aplicação e o resto da infraestrutura: nada impede, hoje, que um caso de uso
 * receba um {@code TNFe} por parâmetro porque foi conveniente, e o dia em que
 * isso acontecer o leiaute da NF-e vira parte da assinatura do sistema — trocar
 * a versão do esquema passa a mexer em regra de negócio.</p>
 *
 * <p>A mesma proibição vale para o próprio JAXB: quem quiser ler XML fora deste
 * pacote está reimplementando a fronteira que já existe.</p>
 */
class ClassesGeradasNaoVazamTest {

    private static final Path RAIZ_DO_CODIGO =
            Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria");

    private static final Path FRONTEIRA_PERMITIDA =
            RAIZ_DO_CODIGO.resolve(Path.of("infraestrutura", "xml"));

    private static final List<String> PACOTES_RESTRITOS = List.of(
            "br.edu.tcc.auditoria.infraestrutura.xml.gerado",
            "jakarta.xml.bind",
            "javax.xml.stream");

    @Test
    void deveEncontrarCodigoForaDaFronteiraParaInspecionar() throws IOException {
        assertThat(arquivosForaDaFronteira()).isNotEmpty();
    }

    @Test
    void somenteInfraestruturaXmlPodeImportarAsClassesGeradasEOJaxb() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String linha = linhas.get(indice).strip();
                if (!linha.startsWith("import ")) {
                    continue;
                }
                int numeroDaLinha = indice + 1;
                PACOTES_RESTRITOS.stream()
                        .filter(linha::contains)
                        .findFirst()
                        .ifPresent(restrito -> violacoes.add(
                                "%s:%d importa %s".formatted(
                                        RAIZ_DO_CODIGO.relativize(arquivo), numeroDaLinha, restrito)));
            }
        }

        assertThat(violacoes)
                .as("Só \"%s\" pode conhecer o leiaute da NF-e. Quem precisar de documento fora dali "
                        + "recebe Documento e ItemDocumento do domínio — ver D005 em "
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
