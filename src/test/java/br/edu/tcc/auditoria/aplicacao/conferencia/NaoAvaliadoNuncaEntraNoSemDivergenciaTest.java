package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.POSSIVEL_DIVERGENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.REQUER_CONFERENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhum contador desta camada devolve "sem divergência mais não concluído".
 *
 * <p>É a regra central do vocabulário da interface, e ela não é verificável
 * lendo os nomes dos métodos: o defeito que importa não é um método chamado
 * {@code conformesMaisNaoAvaliados}, que ninguém escreveria, e sim um método com
 * nome inocente que devolve essa soma.</p>
 *
 * <p>Então a verificação é pelo valor. O cenário é montado com números
 * escolhidos de modo que a soma proibida <strong>não coincida</strong> com
 * nenhum número legítimo, e o teste percorre por reflexão todo método público
 * sem argumento desta camada que devolva número, invocando e conferindo o
 * resultado.</p>
 *
 * <p>Duas autoverificações impedem que ele passe por não ter olhado nada: o
 * número de métodos varridos tem piso, e os valores legítimos conhecidos
 * precisam aparecer entre os colhidos.</p>
 */
class NaoAvaliadoNuncaEntraNoSemDivergenciaTest {

    private static final String PACOTE = "br.edu.tcc.auditoria.aplicacao.conferencia";

    /*
     * Cenário: 2 divergências, 1 requer conferência, 3 não concluídos e 4 sem
     * divergência, cada produto com duas verificações.
     *
     *   produtos      : 2 + 1 + 3 + 4  = 10   sem divergência 4, não concluído 3
     *   verificações  : 2 + 1 + 3 + 14 = 20   sem divergência 14, não concluído 3
     *
     * Somas proibidas: 4 + 3 = 7 no nível do produto, 14 + 3 = 17 no da
     * verificação. Nenhuma delas é 2, 1, 3, 4, 10, 14 ou 20 — é o que torna a
     * conferência por valor conclusiva.
     */
    private static final int SOMA_PROIBIDA_DE_PRODUTOS = 7;
    private static final int SOMA_PROIBIDA_DE_VERIFICACOES = 17;

    @Test
    void nenhumContadorDevolveSemDivergenciaSomadoAoNaoConcluido() {
        ResumoDaConferencia resumo = cenario();

        // O cenário é mesmo o que o comentário acima descreve.
        assertThat(resumo.produtosPorSituacao().quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isEqualTo(4);
        assertThat(resumo.produtosPorSituacao().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isEqualTo(3);
        assertThat(resumo.verificacoesPorEstado().quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isEqualTo(14);
        assertThat(resumo.verificacoesPorEstado().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isEqualTo(3);

        Map<String, Long> colhidos = numerosDe(List.of(
                resumo,
                resumo.produtosPorSituacao(),
                resumo.verificacoesPorEstado(),
                produtoMisto(),
                produtoMisto().contagens()));

        assertThat(colhidos.values())
                .describedAs("algum método devolveu a soma proibida no nível do produto: %s", colhidos)
                .doesNotContain((long) SOMA_PROIBIDA_DE_PRODUTOS);
        assertThat(colhidos.values())
                .describedAs("algum método devolveu a soma proibida no nível da verificação: %s", colhidos)
                .doesNotContain((long) SOMA_PROIBIDA_DE_VERIFICACOES);

        // Autoverificação: a varredura olhou métodos de verdade e leu números de
        // verdade. Sem isto, um filtro errado faria o teste passar vazio.
        assertThat(colhidos)
                .describedAs("a varredura não encontrou contador nenhum")
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(colhidos.values())
                .describedAs("os números legítimos do cenário precisam aparecer entre os colhidos")
                .contains(10L, 20L, 3L);
    }

    @Test
    void oTotalSomaOsQuatroEDizQueEIsso() {
        ResumoDaConferencia resumo = cenario();

        assertThat(resumo.produtosPorSituacao().total())
                .isEqualTo(2 + 1 + 3 + 4)
                .isNotEqualTo(SOMA_PROIBIDA_DE_PRODUTOS);
    }

    private static ResumoDaConferencia cenario() {
        List<SituacaoDoProduto> produtos = new ArrayList<>();
        acrescentar(produtos, 2, POSSIVEL_DIVERGENCIA);
        acrescentar(produtos, 1, REQUER_CONFERENCIA);
        acrescentar(produtos, 3, NAO_FOI_POSSIVEL_CONCLUIR);
        acrescentar(produtos, 4, SEM_DIVERGENCIA_IDENTIFICADA);
        return ResumoDaConferencia.de(produtos);
    }

    private static void acrescentar(
            List<SituacaoDoProduto> produtos, int quantidade, EstadoDeConferencia estado) {
        for (int contador = 0; contador < quantidade; contador++) {
            produtos.add(ConferenciaFicticia.produtoCom(estado, SEM_DIVERGENCIA_IDENTIFICADA));
        }
    }

    private static SituacaoDoProduto produtoMisto() {
        return ConferenciaFicticia.produtoCom(
                POSSIVEL_DIVERGENCIA, NAO_FOI_POSSIVEL_CONCLUIR,
                SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA);
    }

    /** Todo método público sem argumento desta camada que devolva número, invocado. */
    private static Map<String, Long> numerosDe(List<Object> alvos) {
        Map<String, Long> colhidos = new LinkedHashMap<>();
        for (Object alvo : alvos) {
            for (Method metodo : alvo.getClass().getMethods()) {
                if (!ehContadorDestaCamada(metodo)) {
                    continue;
                }
                try {
                    Object devolvido = metodo.invoke(alvo);
                    if (devolvido instanceof Number numero) {
                        colhidos.put(
                                "%s.%s()#%d".formatted(
                                        alvo.getClass().getSimpleName(),
                                        metodo.getName(),
                                        colhidos.size()),
                                numero.longValue());
                    }
                } catch (ReflectiveOperationException naoInvocavel) {
                    throw new AssertionError(
                            "Não foi possível invocar %s.%s()".formatted(
                                    alvo.getClass().getSimpleName(), metodo.getName()),
                            naoInvocavel);
                }
            }
        }
        return colhidos;
    }

    private static boolean ehContadorDestaCamada(Method metodo) {
        if (metodo.getParameterCount() != 0) {
            return false;
        }
        if (metodo.getName().equals("hashCode")) {
            return false;
        }
        Package pacote = metodo.getDeclaringClass().getPackage();
        if (pacote == null || !pacote.getName().equals(PACOTE)) {
            return false;
        }
        Class<?> devolvido = metodo.getReturnType();
        return devolvido == int.class
                || devolvido == long.class
                || devolvido == Integer.class
                || devolvido == Long.class;
    }
}
