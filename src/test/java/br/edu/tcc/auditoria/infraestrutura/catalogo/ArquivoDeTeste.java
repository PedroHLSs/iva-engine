package br.edu.tcc.auditoria.infraestrutura.catalogo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** Abertura dos CSVs fictícios de {@code src/test/resources/catalogo}. */
final class ArquivoDeTeste {

    private ArquivoDeTeste() {
    }

    static Reader csv(String nome) {
        InputStream conteudo = ArquivoDeTeste.class.getResourceAsStream("/catalogo/" + nome);
        if (conteudo == null) {
            throw new IllegalStateException("Arquivo de teste não encontrado no classpath: /catalogo/" + nome);
        }
        return new InputStreamReader(conteudo, StandardCharsets.UTF_8);
    }

    static Reader conteudo(String texto) {
        return new StringReader(texto);
    }
}
