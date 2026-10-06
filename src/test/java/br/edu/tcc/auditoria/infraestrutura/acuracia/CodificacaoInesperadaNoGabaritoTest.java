package br.edu.tcc.auditoria.infraestrutura.acuracia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D024 (04/10/2026): o gabarito passa pelo mesmo ponto de abertura do catálogo. Fora de UTF-8, é recusado com a codificação esperada e a linha, e não com uma MalformedInputException embrulhada. A linha com acento é um comentário, para o caso não depender de nenhuma coluna. Valores fictícios.
class CodificacaoInesperadaNoGabaritoTest {

    private static final String GABARITO = """
            # rótulos fictícios de demonstração
            chave_documento;numero_item;regra_id;rotulo_esperado
            %s;1;R01;ACHADO
            """.formatted("1".repeat(44));

    @TempDir
    Path pasta;

    @Test
    void emUtf8DeveSerLido() throws IOException {
        Path arquivo = escrever(StandardCharsets.UTF_8);

        assertThat(new LeitorDeGabaritoCsv().carregar(arquivo).linhas()).hasSize(1);
    }

    @Test
    void emWindows1252DeveSerRecusadoDizendoACodificacaoEsperada() throws IOException {
        Path arquivo = escrever(Charset.forName("windows-1252"));

        assertThatThrownBy(() -> new LeitorDeGabaritoCsv().carregar(arquivo))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("UTF-8")
                .hasMessageContaining("linha 1")
                .hasMessageContaining("gabarito.csv");
    }

    private Path escrever(Charset codificacao) throws IOException {
        Path arquivo = pasta.resolve("gabarito.csv");
        Files.write(arquivo, GABARITO.getBytes(codificacao));
        return arquivo;
    }
}
