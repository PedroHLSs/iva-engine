package br.edu.tcc.auditoria.infraestrutura.csv;

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

// D024 (04/10/2026): arquivo de texto que entra no sistema abre por AberturaEmUtf8, e por nenhum outro caminho. Um InputStreamReader troca em silêncio o byte que não entende por "?", e foi assim que a web gravou "DESCRI?O FICT?CIA" enquanto a CLI recusava o mesmo arquivo. Este guarda varre src/main/java e acusa quem decodificar texto por fora; comentário e literal de texto não contam, para citar a classe proibida continuar permitido.
class AberturaUnicaDeTextoTest {

    private static final Path PRODUCAO = Path.of("src", "main", "java");
    private static final Pattern PROIBIDO = Pattern.compile("\\bInputStreamReader\\b|\\bnewBufferedReader\\s*\\(");

    @Test
    void nenhumaClasseDeProducaoDeveDecodificarTextoPorForaDaAberturaUnica() throws IOException {
        List<String> violacoes = new ArrayList<>();
        for (Path arquivo : arquivosJava()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int i = 0; i < linhas.size(); i++) {
                if (PROIBIDO.matcher(semComentarioNemLiteral(linhas.get(i))).find()) {
                    violacoes.add(arquivo + ":" + (i + 1) + ": " + linhas.get(i).strip());
                }
            }
        }
        assertThat(violacoes).as("decodificação de texto fora de AberturaEmUtf8").isEmpty();
    }

    // Autoverificação: a varredura olhou de fato o código de produção, inclusive os pontos que abrem arquivo.
    @Test
    void aVarreduraDeveTerOlhadoOCodigoDeProducao() throws IOException {
        List<Path> arquivos = arquivosJava();
        assertThat(arquivos).hasSizeGreaterThan(100);
        assertThat(arquivos).anyMatch(caminho -> caminho.endsWith("FontesDoCatalogo.java"));
        assertThat(arquivos).anyMatch(caminho -> caminho.endsWith("LeitorDeGabaritoCsv.java"));
    }

    // Autoverificação: o padrão acusa o que deve acusar e deixa passar comentário e literal.
    @Test
    void oPadraoDeveAcusarCodigoEDeixarPassarComentarioELiteral() {
        assertThat(PROIBIDO.matcher(semComentarioNemLiteral("return new InputStreamReader(entrada, UTF_8);")).find()).isTrue();
        assertThat(PROIBIDO.matcher(semComentarioNemLiteral("Reader r = Files.newBufferedReader(p, UTF_8);")).find()).isTrue();
        assertThat(PROIBIDO.matcher(semComentarioNemLiteral("// um InputStreamReader troca o byte por ?")).find()).isFalse();
        assertThat(PROIBIDO.matcher(semComentarioNemLiteral(" * newBufferedReader( não é usado aqui")).find()).isFalse();
        assertThat(PROIBIDO.matcher(semComentarioNemLiteral("String s = \"InputStreamReader\";")).find()).isFalse();
    }

    private static List<Path> arquivosJava() throws IOException {
        try (Stream<Path> caminhos = Files.walk(PRODUCAO)) {
            return caminhos.filter(caminho -> caminho.toString().endsWith(".java")).toList();
        }
    }

    // Método auxiliar que descarta comentário de linha, linha de Javadoc e literal de texto.
    private static String semComentarioNemLiteral(String linha) {
        String semEspacos = linha.strip();
        if (semEspacos.startsWith("*") || semEspacos.startsWith("/*")) {
            return "";
        }
        String semLiteral = linha.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
        int comentario = semLiteral.indexOf("//");
        return comentario >= 0 ? semLiteral.substring(0, comentario) : semLiteral;
    }
}
