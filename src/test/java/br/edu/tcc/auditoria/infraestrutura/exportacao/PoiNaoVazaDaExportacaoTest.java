package br.edu.tcc.auditoria.infraestrutura.exportacao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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
 *
 * <h2>Duas varreduras, porque a dependência tem duas grafias</h2>
 *
 * <p>A linha de {@code import} e o nome totalmente qualificado escrito no corpo
 * do arquivo. A segunda entrou em 03/09/2026, na revisão de conformidade das
 * Etapas 5 a 7: até então um retorno declarado como
 * {@code org.apache.poi.ss.usermodel.Workbook} atravessava a fronteira sem ser
 * visto. É a mesma correção que {@code ClassesGeradasNaoVazamTest} recebeu no
 * mesmo dia, e pelo mesmo motivo.</p>
 *
 * <p>Comentários e literais de texto são descartados antes da checagem — citar o
 * POI para explicar por que ele não entra continua permitido. Uma linha de
 * {@code import} é acusada pelas duas varreduras; a redundância é deliberada.</p>
 */
class PoiNaoVazaDaExportacaoTest {

    private static final Path RAIZ_DO_CODIGO =
            Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria");

    private static final Path FRONTEIRA_PERMITIDA =
            RAIZ_DO_CODIGO.resolve(Path.of("infraestrutura", "exportacao"));

    private static final String PACOTE_RESTRITO = "org.apache.poi";

    private static final Pattern TEXTO_LITERAL =
            Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

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

    @Test
    void somenteInfraestruturaExportacaoPodeCitarPoiPeloNomeQualificado() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                if (codigoSemComentarioNemTexto(linhas.get(indice)).contains(PACOTE_RESTRITO)) {
                    violacoes.add("%s:%d cita %s".formatted(
                            RAIZ_DO_CODIGO.relativize(arquivo), indice + 1, PACOTE_RESTRITO));
                }
            }
        }

        assertThat(violacoes)
                .as("Escrever o nome por extenso não é uma forma legítima de contornar a proibição de "
                        + "import: um campo declarado como \"%s.ss.usermodel.Workbook\" leva o formato "
                        + "do arquivo para fora de \"%s\" exatamente como o import levaria — ver D007 "
                        + "em docs/DECISOES-ARQUITETURA.md. Comentários e literais de texto são "
                        + "ignorados nesta varredura.", PACOTE_RESTRITO, FRONTEIRA_PERMITIDA)
                .isEmpty();
    }

    /**
     * Devolve a linha sem comentário e sem literal de texto, ou vazio se a
     * linha inteira é comentário.
     *
     * <p>Repete o auxiliar de {@code ClassesGeradasNaoVazamTest} em vez de
     * compartilhá-lo: os dois guardas fiscalizam fronteiras diferentes, e
     * juntá-los faria uma mudança em qualquer um dos dois mexer no outro.</p>
     */
    private static String codigoSemComentarioNemTexto(String linhaBruta) {
        String linha = linhaBruta.strip();
        if (linha.startsWith("*") || linha.startsWith("/*") || linha.startsWith("//")) {
            return "";
        }
        String semTexto = TEXTO_LITERAL.matcher(linha).replaceAll("\"\"");
        int inicioDoComentario = semTexto.indexOf("//");
        return inicioDoComentario < 0 ? semTexto : semTexto.substring(0, inicioDoComentario);
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
