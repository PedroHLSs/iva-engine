package br.edu.tcc.auditoria.dominio;

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
 * Guarda automatizada da decisão D001: o domínio não depende de framework.
 *
 * <p>A regra é fácil de violar sem perceber — basta uma anotação conveniente
 * para serializar, mapear ou injetar. Uma revisão de código pega isso enquanto
 * alguém lembra da regra; este teste pega sempre.</p>
 *
 * <p>A verificação é pela positiva: código de domínio só pode importar a
 * biblioteca padrão do Java e o próprio domínio. Qualquer outro import falha,
 * inclusive os que ainda não existem no projeto.</p>
 */
class DominioNaoDependeDeFrameworkTest {

    private static final Path RAIZ_DO_DOMINIO =
            Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria", "dominio");

    private static final String PREFIXO_DA_BIBLIOTECA_PADRAO = "java.";
    private static final String PREFIXO_DO_PROPRIO_DOMINIO = "br.edu.tcc.auditoria.dominio";

    @Test
    void deveEncontrarArquivosDeDominioParaInspecionar() throws IOException {
        assertThat(arquivosDeDominio())
                .as("O teste precisa enxergar o código de domínio a partir de %s", RAIZ_DO_DOMINIO.toAbsolutePath())
                .isNotEmpty();
    }

    @Test
    void naoDeveImportarNadaAlemDaBibliotecaPadraoEDoProprioDominio() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosDeDominio()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String importado = pacoteImportado(linhas.get(indice));
                if (importado == null || ehPermitido(importado)) {
                    continue;
                }
                violacoes.add("%s:%d importa %s".formatted(RAIZ_DO_DOMINIO.relativize(arquivo), indice + 1, importado));
            }
        }

        assertThat(violacoes)
                .as("O domínio só pode importar pacotes \"java.*\" e ele mesmo. Nada de Spring, JPA, "
                        + "Jackson ou JAXB — ver D001 em docs/DECISOES-ARQUITETURA.md. Pacotes "
                        + "\"javax.*\" também caem aqui, de propósito: são biblioteca padrão, mas o "
                        + "domínio deste projeto não tem razão para usá-los, e o alerta força revisão.")
                .isEmpty();
    }

    private static List<Path> arquivosDeDominio() throws IOException {
        try (Stream<Path> caminhos = Files.walk(RAIZ_DO_DOMINIO)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(caminho -> caminho.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    /** Devolve o pacote importado pela linha, ou {@code null} se a linha não é um import. */
    private static String pacoteImportado(String linha) {
        String semEspacos = linha.strip();
        if (!semEspacos.startsWith("import ")) {
            return null;
        }
        return semEspacos.substring("import ".length())
                .replaceFirst("^static\\s+", "")
                .replace(";", "")
                .strip();
    }

    private static boolean ehPermitido(String importado) {
        return importado.startsWith(PREFIXO_DA_BIBLIOTECA_PADRAO)
                || importado.startsWith(PREFIXO_DO_PROPRIO_DOMINIO);
    }
}
