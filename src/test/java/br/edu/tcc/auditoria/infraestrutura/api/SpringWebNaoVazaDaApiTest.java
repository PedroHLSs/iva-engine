package br.edu.tcc.auditoria.infraestrutura.api;

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
 * HTTP e JSON são detalhe de saída, e só {@code infraestrutura/api} pode
 * conhecê-los.
 *
 * <p>Mesma fronteira que {@code PoiNaoVazaDaExportacaoTest} guarda para o formato
 * da planilha e que {@code ClassesGeradasNaoVazamTest} guarda para o leiaute da
 * NF-e, pelo mesmo motivo. O risco aqui é o mais tentador dos três: nada impede,
 * hoje, que um caso de uso ganhe uma anotação {@code @JsonProperty} porque o nome
 * do campo ficou feio na resposta, ou que uma porta passe a devolver
 * {@code ResponseEntity} porque foi conveniente. No dia em que isso acontecer, o
 * formato da resposta HTTP passa a ser parte da regra de negócio.</p>
 *
 * <p>O Jackson é o que mais importa vigiar. A D001 proíbe anotação de serialização
 * no domínio desde a Etapa 0, e até a Etapa 7 essa proibição era barata porque o
 * Jackson não estava no classpath. Agora está, trazido pelo
 * {@code spring-boot-starter-web} — e uma proibição só vale quando o que ela proíbe
 * é possível.</p>
 *
 * <h2>Duas varreduras, porque a dependência tem duas grafias</h2>
 *
 * <p>A linha de {@code import} e o nome totalmente qualificado escrito no corpo do
 * arquivo. As duas entram juntas aqui: os dois guardas anteriores nasceram só com a
 * primeira e precisaram de correção meses depois, quando um retorno declarado por
 * extenso atravessou a fronteira sem ser visto. Não repetir o erro é mais barato
 * que corrigi-lo.</p>
 *
 * <p>Comentários e literais de texto são descartados antes da checagem — citar o
 * Spring MVC para explicar por que ele não entra continua permitido. Uma linha de
 * {@code import} é acusada pelas duas varreduras; a redundância é deliberada.</p>
 *
 * <p>O auxiliar que descarta comentário e literal é <strong>duplicado</strong> dos
 * outros dois guardas, e não extraído para um lugar comum: compartilhá-lo obrigaria
 * a mexer em teste de etapa anterior, e cada guarda fiscaliza uma fronteira
 * diferente.</p>
 */
class SpringWebNaoVazaDaApiTest {

    private static final Path RAIZ_DO_CODIGO =
            Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria");

    private static final Path FRONTEIRA_PERMITIDA = RAIZ_DO_CODIGO.resolve(
            Path.of("infraestrutura", "api"));

    /**
     * Os dois pacotes que param na fronteira.
     *
     * <p>{@code org.springframework.web} é o Spring MVC. Não inclui
     * {@code org.springframework.boot.web}, que é outro pacote: é dele que sai o
     * {@code WebServerApplicationContext} que {@code ComandoServir} consulta para
     * saber se há servidor, e essa pergunta é legítima na CLI.</p>
     */
    private static final List<String> PACOTES_RESTRITOS =
            List.of("org.springframework.web", "com.fasterxml.jackson");

    private static final Pattern TEXTO_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    @Test
    void deveEncontrarCodigoForaDaFronteiraParaInspecionar() throws IOException {
        assertThat(arquivosForaDaFronteira())
                .as("sem isto, um erro de caminho faria as outras duas varreduras passarem por não "
                        + "terem olhado nada — a forma mais silenciosa de um guarda deixar de guardar")
                .isNotEmpty();
    }

    @Test
    void somenteInfraestruturaApiPodeImportarSpringWebOuJackson() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String linha = linhas.get(indice).strip();
                if (!linha.startsWith("import ")) {
                    continue;
                }
                for (String restrito : PACOTES_RESTRITOS) {
                    if (linha.contains(restrito)) {
                        violacoes.add("%s:%d importa %s".formatted(
                                RAIZ_DO_CODIGO.relativize(arquivo), indice + 1, restrito));
                    }
                }
            }
        }

        assertThat(violacoes)
                .as("Só \"%s\" pode conhecer HTTP e JSON. Quem precisar expor dado fora dali monta um "
                        + "DTO nesse pacote — ver D001 e D009 em docs/DECISOES-ARQUITETURA.md.",
                        FRONTEIRA_PERMITIDA)
                .isEmpty();
    }

    @Test
    void somenteInfraestruturaApiPodeCitarSpringWebOuJacksonPeloNomeQualificado() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String codigo = codigoSemComentarioNemTexto(linhas.get(indice));
                for (String restrito : PACOTES_RESTRITOS) {
                    if (codigo.contains(restrito)) {
                        violacoes.add("%s:%d cita %s".formatted(
                                RAIZ_DO_CODIGO.relativize(arquivo), indice + 1, restrito));
                    }
                }
            }
        }

        assertThat(violacoes)
                .as("Escrever o nome por extenso não é forma legítima de contornar a proibição de "
                        + "import: um campo declarado como "
                        + "\"com.fasterxml.jackson.annotation.JsonProperty\" leva o formato da resposta "
                        + "para fora de \"%s\" exatamente como o import levaria. Comentários e "
                        + "literais de texto são ignorados nesta varredura.", FRONTEIRA_PERMITIDA)
                .isEmpty();
    }

    /**
     * Devolve a linha sem comentário e sem literal de texto, ou vazio se a linha
     * inteira é comentário.
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
