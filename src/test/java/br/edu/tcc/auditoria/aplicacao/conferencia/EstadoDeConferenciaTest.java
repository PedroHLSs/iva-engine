package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O vocabulário da interface é conteúdo, não estilo — então é afirmado em teste.
 */
class EstadoDeConferenciaTest {

    @Test
    void deveEscreverExatamenteOsQuatroRotulosDoVocabulario() {
        assertThat(EstadoDeConferencia.values()).hasSize(4);
        assertThat(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA.rotulo())
                .isEqualTo("Sem divergência identificada");
        assertThat(EstadoDeConferencia.REQUER_CONFERENCIA.rotulo())
                .isEqualTo("Requer conferência");
        assertThat(EstadoDeConferencia.POSSIVEL_DIVERGENCIA.rotulo())
                .isEqualTo("Possível divergência");
        assertThat(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR.rotulo())
                .isEqualTo("Não foi possível concluir");
    }

    /**
     * "Conferido" afirmaria uma verificação que não aconteceu.
     *
     * <p>O sistema aplica as regras que tem, sobre os campos que elas leem. É o
     * mesmo cuidado que o R04 já toma ao recusar chamar de erro um tratamento não
     * aproveitado.</p>
     */
    @Test
    void nenhumRotuloOuExplicacaoDeveAfirmarQueOProdutoFoiConferido() {
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            assertThat(minusculo(estado.rotulo()))
                    .describedAs("rótulo de %s", estado.name())
                    .doesNotContain("conferido")
                    .doesNotContain("conferida")
                    .doesNotContain("verificado");
            assertThat(minusculo(estado.explicacao()))
                    .describedAs("explicação de %s", estado.name())
                    .doesNotContain("conferido")
                    .doesNotContain("conferida");
        }
    }

    @Test
    void todoEstadoDeveExplicarOQueQuerDizer() {
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            assertThat(estado.rotulo()).isNotBlank();
            assertThat(estado.explicacao())
                    .describedAs("um rótulo de quatro palavras sozinho convida quem lê a inventar "
                            + "o significado")
                    .isNotBlank();
        }
    }

    /**
     * A ordem de declaração é a precedência, e {@link SituacaoDoProduto} depende
     * dela. Reordenar as constantes muda o comportamento do sistema, então a
     * ordem é afirmada aqui, onde quem reordenar vai ver.
     */
    @Test
    void aOrdemDeDeclaracaoDeveSerAPrecedenciaDaMaisForteParaAMaisFraca() {
        assertThat(EstadoDeConferencia.values()).containsExactly(
                EstadoDeConferencia.POSSIVEL_DIVERGENCIA,
                EstadoDeConferencia.REQUER_CONFERENCIA,
                EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR,
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA);

        assertThat(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA.ordinal())
                .describedAs("sem divergência precisa ser o último: é o que faz um produto só "
                        + "alcançá-lo quando todas as verificações o alcançaram")
                .isEqualTo(EstadoDeConferencia.values().length - 1);
    }

    private static String minusculo(String texto) {
        return texto.toLowerCase(Locale.ROOT);
    }
}
