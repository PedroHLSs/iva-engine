package br.edu.tcc.auditoria.infraestrutura.csv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Correção de 04/10/2026 (D025): elemento vazio numa lista separada por | é recusado com a linha e a coluna, em vez de sumir. "AAA||BBB" e "AAA|" costumam ser um valor apagado por engano, e descartá-lo em silêncio fazia a classificação aceitar menos CSTs do que a pessoa escreveu. As outras colunas de lista do catálogo já recusavam assim desde a D015. Valores fictícios.
class ElementoVazioNaListaTest {

    @Test
    void doisSeparadoresSeguidosDevemSerRecusados() throws IOException {
        LinhaCsv linha = linha("AAA||BBB");

        assertThatThrownBy(() -> linha.lista("csts"))
                .isInstanceOf(Recusada.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("\"csts\"")
                .hasMessageContaining("vazio");
    }

    @Test
    void separadorSobrandoNoFimDeveSerRecusado() throws IOException {
        LinhaCsv linha = linha("AAA|");

        assertThatThrownBy(() -> linha.lista("csts")).isInstanceOf(Recusada.class).hasMessageContaining("vazio");
    }

    @Test
    void separadorSobrandoNoComecoDeveSerRecusado() throws IOException {
        LinhaCsv linha = linha("|AAA");

        assertThatThrownBy(() -> linha.lista("csts")).isInstanceOf(Recusada.class).hasMessageContaining("vazio");
    }

    // Controle: a lista bem formada, inclusive com espaço em volta, como sempre foi; célula em branco continua lista vazia.
    @Test
    void listaBemFormadaDeveContinuarComoEra() throws IOException {
        assertThat(linha("AAA | BBB").lista("csts")).containsExactly("AAA", "BBB");
        assertThat(linha("AAA").lista("csts")).containsExactly("AAA");
        assertThat(linha("").lista("csts")).isEmpty();
    }

    private static LinhaCsv linha(String valor) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(new StringReader("csts;outra\n" + valor + ";x\n"),
                (mensagem, causa) -> new Recusada(mensagem));
        return linhas.get(0);
    }

    private static final class Recusada extends RuntimeException {
        Recusada(String mensagem) {
            super(mensagem);
        }
    }
}
