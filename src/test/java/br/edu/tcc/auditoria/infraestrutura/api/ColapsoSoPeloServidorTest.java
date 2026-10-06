package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guarda de código-fonte da regra de colapso (Etapa 13, D014).
 *
 * <p>Quem decide o que nasce recolhido é o servidor, pela {@code RegraDeColapso}.
 * A interface só obedece. Este teste é o que faz a regra sobreviver à próxima
 * tela que alguém escrever:</p>
 *
 * <ol>
 *   <li>só {@code js/colapso.js} cria {@code <details>} — nenhuma tela recolhe
 *       conteúdo por conta própria;</li>
 *   <li>{@code js/colapso.js} não menciona estado nenhum, e lê o campo que o
 *       servidor manda;</li>
 *   <li>nenhum módulo compara estado ou situação com um código literal — que é o
 *       jeito de uma tela decidir sozinha o que esconder.</li>
 * </ol>
 *
 * <p>Comentários são descartados antes da checagem: citar a regra para explicá-la
 * continua permitido. E a resposta da API recusa, no construtor, recolher o que
 * não é "sem divergência identificada" — a segunda barreira, também testada aqui.</p>
 */
class ColapsoSoPeloServidorTest {

    private static final Path JS = Path.of("src", "main", "resources", "static", "js");
    private static final Path AUXILIAR = JS.resolve("colapso.js");
    private static final Pattern CRIA_DETAILS = Pattern.compile("['\"`]details['\"`]|<details");
    private static final Pattern COMPARA_ESTADO = Pattern.compile(
            "\\.(estado|situacao)\\s*[!=]==?\\s*['\"`]|['\"`]\\s*[!=]==?\\s*[\\w.]*\\.(estado|situacao)\\b");

    @Test
    void soOAuxiliarDeColapsoCriaDetails() throws IOException {
        List<String> acusacoes = new ArrayList<>();
        List<Path> arquivos = modulos();
        for (Path arquivo : arquivos) {
            if (arquivo.endsWith("colapso.js")) {
                continue;
            }
            String codigo = semComentarios(Files.readString(arquivo, StandardCharsets.UTF_8));
            if (CRIA_DETAILS.matcher(codigo).find()) {
                acusacoes.add(arquivo.toString());
            }
        }
        assertThat(arquivos).as("autoverificação: a varredura precisa ter aberto os módulos").hasSizeGreaterThan(20);
        assertThat(CRIA_DETAILS.matcher(semComentarios(Files.readString(AUXILIAR, StandardCharsets.UTF_8))).find())
                .as("autoverificação: o auxiliar cria details, senão o padrão acima não pega nada")
                .isTrue();
        assertThat(acusacoes)
                .as("recolher conteúdo passa por js/colapso.js, que só obedece ao servidor")
                .isEmpty();
    }

    @Test
    void oAuxiliarNaoMencionaEstadoELeOCampoDoServidor() throws IOException {
        String codigo = semComentarios(Files.readString(AUXILIAR, StandardCharsets.UTF_8));

        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            assertThat(codigo).as("o auxiliar não pode conhecer o estado %s", estado).doesNotContain(estado.name());
        }
        assertThat(codigo).doesNotContainPattern("\\bestado\\b").doesNotContainPattern("\\bsituacao\\b");
        assertThat(codigo)
                .as("nasce recolhido só com true explícito do servidor; o resto nasce aberto")
                .contains("recolhidoPorPadrao === true");
    }

    @Test
    void nenhumModuloComparaEstadoComCodigoLiteral() throws IOException {
        List<String> acusacoes = new ArrayList<>();
        for (Path arquivo : modulos()) {
            String[] linhas = semComentarios(Files.readString(arquivo, StandardCharsets.UTF_8)).split("\n");
            for (int numero = 0; numero < linhas.length; numero++) {
                if (COMPARA_ESTADO.matcher(linhas[numero]).find()) {
                    acusacoes.add(arquivo + ":" + (numero + 1) + " — " + linhas[numero].strip());
                }
            }
        }
        assertThat(COMPARA_ESTADO.matcher("if (item.estado === 'NAO_FOI_POSSIVEL_CONCLUIR') {").find())
                .as("autoverificação: o padrão pega a comparação que existe para pegar")
                .isTrue();
        assertThat(acusacoes).as("a tela não decide por estado; o servidor manda o campo").isEmpty();
    }

    @Test
    void aRespostaRecusaRecolherVerificacaoQueNaoSejaSemDivergencia() {
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            if (estado == EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA) {
                continue;
            }
            assertThatThrownBy(() -> new ProdutoExposto.VerificacaoExposta(
                    "R99-ficticia", null, "motivo fictício", "0.0.0", null, estado.name(), "rótulo fictício", true))
                    .as("verificação em %s não nasce recolhida", estado)
                    .isInstanceOf(RespostaInvalida.class)
                    .hasMessageContaining("não pode nascer recolhido");
        }
    }

    private static List<Path> modulos() throws IOException {
        try (Stream<Path> caminhos = Files.walk(JS)) {
            return caminhos.filter(caminho -> caminho.toString().endsWith(".js")).sorted().toList();
        }
    }

    /** Tira comentários de bloco e de linha. Não mexe em texto entre aspas que contenha as duas barras. */
    private static String semComentarios(String codigo) {
        String semBlocos = codigo.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder limpo = new StringBuilder();
        for (String linha : semBlocos.split("\n", -1)) {
            Matcher comentario = Pattern.compile("^\\s*//.*$").matcher(linha);
            limpo.append(comentario.matches() ? "" : linha).append('\n');
        }
        return limpo.toString();
    }
}
