package br.edu.tcc.auditoria;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// CLAUDE.md, seção 5, e D017 (03/10/2026): nenhum código normativo escrito em código de produção. Varre src/main/java atrás de literal de texto só com dígitos, no comprimento de CST (3), de cClassTrib (6) ou de NCM (8). Comentário não conta. Até 03/10/2026 a R05 trazia os CST 210 e 222 assim, e nada acusava.
class NenhumCodigoNormativoEmCodigoTest {

    private static final Path PRODUCAO = Path.of("src", "main", "java");

    private static final Pattern LITERAL_SO_DE_DIGITOS = Pattern.compile("\"(\\d{3}|\\d{6}|\\d{8})\"");

    // Literais permitidos, cada um com o motivo: não são código da norma.
    private static final Set<String> PERMITIDOS = Set.of(
            // A R05 divide por cem para ler o percentual do catálogo como porcentagem (D004).
            "dominio/regras/RegraValorDeTributoConfere.java:\"100\"");

    @Test
    void nenhumArquivoDeProducaoDeveTrazerCodigoNormativoComoLiteral() throws IOException {
        List<String> encontrados = new ArrayList<>();
        for (Path arquivo : arquivosJava()) {
            String semComentarios = semComentarios(Files.readString(arquivo, StandardCharsets.UTF_8));
            Matcher literal = LITERAL_SO_DE_DIGITOS.matcher(semComentarios);
            while (literal.find()) {
                String chave = relativo(arquivo) + ":" + literal.group();
                if (!PERMITIDOS.contains(chave)) {
                    encontrados.add(chave);
                }
            }
        }

        assertThat(encontrados)
                .describedAs("código normativo entra por CSV (CLAUDE.md, seção 5); se for inevitável, vira exceção registrada em ADR e entra em PERMITIDOS com o motivo")
                .isEmpty();
    }

    // Sem esta conferência, um caminho errado faria o teste passar por não ter olhado arquivo nenhum.
    @Test
    void aVarreduraDeveEncontrarOsArquivosDeProducao() throws IOException {
        List<Path> arquivos = arquivosJava();

        assertThat(arquivos.size()).isGreaterThan(400);
        assertThat(arquivos).anyMatch(arquivo -> relativo(arquivo).equals("dominio/regras/RegraValorDeTributoConfere.java"));
    }

    // E o padrão precisa acusar o que diz acusar, e deixar passar comentário.
    @Test
    void oPadraoDeveAcusarLiteralECalarComentario() {
        assertThat(LITERAL_SO_DE_DIGITOS.matcher(semComentarios("Set.of(\"222\");")).find()).isTrue();
        assertThat(LITERAL_SO_DE_DIGITOS.matcher(semComentarios("x = \"000001\";")).find()).isTrue();
        assertThat(LITERAL_SO_DE_DIGITOS.matcher(semComentarios("// o CST \"222\" vem do catálogo")).find()).isFalse();
        assertThat(LITERAL_SO_DE_DIGITOS.matcher(semComentarios("/* \"210\" */ int a;")).find()).isFalse();
    }

    private static List<Path> arquivosJava() throws IOException {
        try (Stream<Path> caminhos = Files.walk(PRODUCAO)) {
            return caminhos.filter(caminho -> caminho.toString().endsWith(".java")).toList();
        }
    }

    private static String relativo(Path arquivo) {
        return PRODUCAO.resolve("br/edu/tcc/auditoria").relativize(arquivo).toString().replace('\\', '/');
    }

    // Método auxiliar que tira comentários de linha e de bloco; texto entre aspas fica, porque é ele que se procura.
    private static String semComentarios(String fonte) {
        return fonte.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
