package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A tabela de tradução, conferida combinação a combinação.
 *
 * <p>Se ela estiver errada, a interface mente, e mente com aparência de
 * conclusão firme. Por isso o teste percorre o produto cartesiano inteiro —
 * três desfechos vezes quatro severidades mais a ausência de severidade — e não
 * uma amostra dos casos que alguém lembrou de escrever.</p>
 *
 * <p>A tabela esperada está escrita <strong>aqui</strong>, à mão, e não obtida
 * de {@link TraducaoDeDesfecho}: um teste que perguntasse ao código qual é a
 * resposta certa mediria o código contra si mesmo.</p>
 */
class TraducaoDeDesfechoTest {

    /** A tradução esperada, escrita à mão. Nenhuma linha vem do código de produção. */
    private static final Map<String, EstadoDeConferencia> ESPERADO = Map.of(
            "CONFORME|(sem severidade)", EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
            "NAO_AVALIADO|(sem severidade)", EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR,
            "ACHADO|CRITICA", EstadoDeConferencia.POSSIVEL_DIVERGENCIA,
            "ACHADO|GRAVE", EstadoDeConferencia.POSSIVEL_DIVERGENCIA,
            "ACHADO|MODERADA", EstadoDeConferencia.POSSIVEL_DIVERGENCIA,
            "ACHADO|INFORMATIVA", EstadoDeConferencia.REQUER_CONFERENCIA);

    @Test
    void deveTraduzirTodaCombinacaoDeDesfechoESeveridade() {
        List<String> conferidas = new ArrayList<>();

        for (ResultadoAvaliacao resultado : ResultadoAvaliacao.values()) {
            for (Optional<Severidade> severidade : severidadesPossiveis()) {
                String combinacao = combinacao(resultado, severidade);
                EstadoDeConferencia esperado = ESPERADO.get(combinacao);

                if (esperado == null) {
                    assertThatThrownBy(() -> TraducaoDeDesfecho.de(resultado, severidade))
                            .describedAs("a combinação %s não existe no modelo e não pode "
                                    + "receber um estado por conveniência", combinacao)
                            .isInstanceOf(ConferenciaInvalida.class);
                } else {
                    assertThat(TraducaoDeDesfecho.de(resultado, severidade))
                            .describedAs("combinação %s", combinacao)
                            .isEqualTo(esperado);
                }
                conferidas.add(combinacao);
            }
        }

        // Sem isto, um erro de laço faria o teste passar por não ter conferido
        // nada — que é a forma mais silenciosa de um guarda deixar de guardar.
        assertThat(conferidas)
                .describedAs("três desfechos vezes cinco severidades possíveis")
                .hasSize(ResultadoAvaliacao.values().length * (Severidade.values().length + 1));
        assertThat(conferidas).containsAll(ESPERADO.keySet());
    }

    @Test
    void deveTratarSeveridadeInformativaComoRequerConferenciaENaoComoDivergencia() {
        assertThat(TraducaoDeDesfecho.de(
                ResultadoAvaliacao.ACHADO, Optional.of(Severidade.INFORMATIVA)))
                .isEqualTo(EstadoDeConferencia.REQUER_CONFERENCIA);

        assertThat(TraducaoDeDesfecho.de(
                ResultadoAvaliacao.ACHADO, Optional.of(Severidade.INFORMATIVA)))
                .isNotEqualTo(EstadoDeConferencia.POSSIVEL_DIVERGENCIA);
    }

    @Test
    void deveRecusarApontamentoSemSeveridade() {
        assertThatThrownBy(() -> TraducaoDeDesfecho.de(ResultadoAvaliacao.ACHADO, Optional.empty()))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("severidade");
    }

    @Test
    void deveRecusarConformeAcompanhadoDeSeveridade() {
        assertThatThrownBy(() -> TraducaoDeDesfecho.de(
                ResultadoAvaliacao.CONFORME, Optional.of(Severidade.CRITICA)))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("CONFORME");
    }

    @Test
    void deveRecusarNaoAvaliadoAcompanhadoDeSeveridade() {
        assertThatThrownBy(() -> TraducaoDeDesfecho.de(
                ResultadoAvaliacao.NAO_AVALIADO, Optional.of(Severidade.INFORMATIVA)))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("NAO_AVALIADO");
    }

    @Test
    void deveRecusarDesfechoNuloESeveridadeNula() {
        assertThatThrownBy(() -> TraducaoDeDesfecho.de(null, Optional.empty()))
                .isInstanceOf(ConferenciaInvalida.class);
        assertThatThrownBy(() -> TraducaoDeDesfecho.de(ResultadoAvaliacao.CONFORME, null))
                .isInstanceOf(ConferenciaInvalida.class);
        assertThatThrownBy(() -> TraducaoDeDesfecho.de(null))
                .isInstanceOf(ConferenciaInvalida.class);
    }

    @Test
    void deveTraduzirAvaliacaoDoMotorTomandoASeveridadeDoProprioApontamento() {
        assertThat(TraducaoDeDesfecho.de(
                ConferenciaFicticia.comAchado("RXX01", Severidade.CRITICA)))
                .isEqualTo(EstadoDeConferencia.POSSIVEL_DIVERGENCIA);

        assertThat(TraducaoDeDesfecho.de(
                ConferenciaFicticia.comAchado("RXX02", Severidade.INFORMATIVA)))
                .isEqualTo(EstadoDeConferencia.REQUER_CONFERENCIA);

        assertThat(TraducaoDeDesfecho.de(ConferenciaFicticia.conforme("RXX03")))
                .isEqualTo(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA);

        assertThat(TraducaoDeDesfecho.de(ConferenciaFicticia.naoAvaliada("RXX04")))
                .isEqualTo(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR);
    }

    /**
     * Severidade nova no domínio não pode chegar à interface sem alguém decidir
     * de que lado ela fica.
     *
     * <p>A tradução já falha a compilação nesse caso, porque o {@code switch}
     * dela é exaustivo e sem {@code default}. Este teste guarda o outro lado: a
     * tabela esperada <em>deste arquivo</em> envelheceria em silêncio, e o teste
     * acima passaria conferindo uma severidade a menos.</p>
     */
    @Test
    void deveCobrirTodasAsSeveridadesQueODominioDeclara() {
        for (Severidade severidade : Severidade.values()) {
            String combinacao = "ACHADO|" + severidade.name();
            assertThat(ESPERADO)
                    .describedAs("a severidade %s não está na tabela esperada deste teste; "
                            + "decida se ela é divergência ou pedido de conferência", severidade)
                    .containsKey(combinacao);
        }
        assertThatCode(() -> Severidade.valueOf("CRITICA")).doesNotThrowAnyException();
    }

    private static List<Optional<Severidade>> severidadesPossiveis() {
        List<Optional<Severidade>> possiveis = new ArrayList<>();
        possiveis.add(Optional.empty());
        for (Severidade severidade : Severidade.values()) {
            possiveis.add(Optional.of(severidade));
        }
        return possiveis;
    }

    private static String combinacao(ResultadoAvaliacao resultado, Optional<Severidade> severidade) {
        return resultado.name() + "|" + severidade.map(Severidade::name).orElse("(sem severidade)");
    }
}
