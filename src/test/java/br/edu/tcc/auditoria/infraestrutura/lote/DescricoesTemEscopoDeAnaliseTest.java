package br.edu.tcc.auditoria.infraestrutura.lote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O acumulador de descrições é de uma análise, e nunca do processo.
 *
 * <h2>Por que um guarda de código-fonte, e não um teste de comportamento</h2>
 *
 * <p>Acúmulo não tem sintoma observável de fora: um registro compartilhado
 * continua devolvendo a descrição certa para cada item, porque o endereço é o
 * resumo do item. O que ele faz é segurar, pelo tempo que o servidor ficar de pé,
 * o texto livre de toda nota que passou — e isso nenhuma asserção de resposta
 * pega.</p>
 *
 * <p>O defeito, então, é de forma: basta alguém promover o acumulador a campo de
 * um componente do Spring, ou a {@code @Bean}, e ele passa a viver enquanto o
 * processo viver. É exatamente o que aconteceu com o registro de falhas da Etapa
 * 4, que era assim e precisou ser isolado quando a API passou a existir. Este
 * guarda existe para que não aconteça de novo em silêncio.</p>
 *
 * <p>É a mesma técnica de {@code PoiNaoVazaDaExportacaoTest} e dos guardas de
 * fronteira das Etapas 1 e 4: varre o código-fonte e acusa arquivo e linha.</p>
 */
@DisplayName("Guarda: o acumulador de descrições não vira estado de processo")
class DescricoesTemEscopoDeAnaliseTest {

    private static final Path CODIGO = Path.of("src", "main", "java");
    private static final String ACUMULADOR = "DescricoesDeProdutoEmMemoria";

    private static final List<String> ANOTACOES_DE_COMPONENTE =
            List.of("@Component", "@Service", "@Repository", "@Bean", "@Configuration");

    @Test
    void oAcumuladorNaoPodeSerCampoDeUmComponenteDoSpring() throws IOException {
        List<String> acusacoes = new ArrayList<>();

        for (Path arquivo : arquivosJava()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            boolean ehComponente = linhas.stream()
                    .anyMatch(linha -> ANOTACOES_DE_COMPONENTE.stream().anyMatch(linha::contains));

            for (int numero = 0; numero < linhas.size(); numero++) {
                String linha = linhas.get(numero);
                if (!ehCampo(linha) || !linha.contains(ACUMULADOR)) {
                    continue;
                }
                if (ehComponente) {
                    acusacoes.add("%s:%d — %s".formatted(arquivo, numero + 1, linha.strip()));
                }
            }
        }

        assertThat(acusacoes)
                .describedAs("campo de componente do Spring vive enquanto o processo vive")
                .isEmpty();
    }

    @Test
    void oAcumuladorSoPodeNascerDentroDaFabricaDeLeitura() throws IOException {
        List<String> criacoes = new ArrayList<>();

        for (Path arquivo : arquivosJava()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int numero = 0; numero < linhas.size(); numero++) {
                if (linhas.get(numero).contains("new " + ACUMULADOR + "(")) {
                    criacoes.add(arquivo.getFileName().toString());
                }
            }
        }

        assertThat(criacoes)
                .describedAs("quem cria o acumulador é quem começa uma análise, e mais ninguém")
                .containsExactly("FabricaDeLeituraDeLoteIsolada.java");
    }

    @Test
    void aFabricaDeveCriarUmAcumuladorNovoACadaLeitura() throws IOException {
        Path fabrica = CODIGO.resolve(
                "br/edu/tcc/auditoria/infraestrutura/lote/FabricaDeLeituraDeLoteIsolada.java");
        String fonte = Files.readString(fabrica, StandardCharsets.UTF_8);

        int inicioDoMetodo = fonte.indexOf("public LeituraDeLote nova()");
        assertThat(inicioDoMetodo)
                .describedAs("autoverificação: o método que cria a leitura precisa existir")
                .isNotNegative();

        String corpo = fonte.substring(inicioDoMetodo, fonte.indexOf("\n    }", inicioDoMetodo));
        assertThat(corpo)
                .describedAs("nasce dentro da chamada, e morre com a análise")
                .contains("new " + ACUMULADOR + "()");
        assertThat(fonte)
                .describedAs("e não é campo da fábrica, que é um componente de vida longa")
                .doesNotContain("private final " + ACUMULADOR);
    }

    /**
     * Declaração de campo, e não assinatura de método.
     *
     * <p>O parêntese sozinho não serve para distinguir os dois: um campo com
     * inicializador — {@code private final X x = new X();} — tem parêntese e é
     * campo, e foi exatamente por aí que a primeira versão deste guarda deixou
     * passar a sabotagem. O que separa é onde o parêntese está: num campo com
     * inicializador ele vem depois do {@code =}; numa assinatura, antes.</p>
     */
    private static boolean ehCampo(String linha) {
        String limpa = linha.strip();
        boolean declaracao = limpa.startsWith("private ") || limpa.startsWith("static ")
                || limpa.startsWith("public ") || limpa.startsWith("protected ");
        if (!declaracao || !limpa.endsWith(";")) {
            return false;
        }
        int parentese = limpa.indexOf('(');
        int atribuicao = limpa.indexOf('=');
        if (parentese < 0) {
            return true;
        }
        return atribuicao >= 0 && atribuicao < parentese;
    }

    private static List<Path> arquivosJava() throws IOException {
        try (Stream<Path> caminhos = Files.walk(CODIGO)) {
            return caminhos
                    .filter(Files::isRegularFile)
                    .filter(arquivo -> arquivo.toString().endsWith(".java"))
                    .filter(arquivo -> !arquivo.getFileName().toString().equals(ACUMULADOR + ".java"))
                    .toList();
        } catch (UncheckedIOException naoLeu) {
            throw new IOException("Não foi possível varrer o código-fonte.", naoLeu);
        }
    }
}
