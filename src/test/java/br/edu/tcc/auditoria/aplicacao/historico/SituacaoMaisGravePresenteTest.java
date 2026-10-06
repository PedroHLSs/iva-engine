package br.edu.tcc.auditoria.aplicacao.historico;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A situação do histórico é a mais grave presente, e não a da maioria.
 *
 * <p>O teste por HTTP do histórico usa análises de um produto só, em que as duas
 * leituras coincidem — e por isso não distinguia uma da outra. Este caso separa:
 * seis produtos sem divergência e quatro não concluídos.</p>
 */
class SituacaoMaisGravePresenteTest {

    @Test
    void seisSemDivergenciaEQuatroNaoConcluidosENaoFoiPossivelConcluir() {
        Map<EstadoDeConferencia, Integer> porEstado = zerado();
        porEstado.put(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA, 6);
        porEstado.put(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR, 4);

        assertThat(ServicoDoHistorico.situacaoMaisGrave(porEstado))
                .contains(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR);
    }

    @Test
    void umaDivergenciaEmMeioAMuitasConformidadesEPossivelDivergencia() {
        Map<EstadoDeConferencia, Integer> porEstado = zerado();
        porEstado.put(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA, 99);
        porEstado.put(EstadoDeConferencia.POSSIVEL_DIVERGENCIA, 1);

        assertThat(ServicoDoHistorico.situacaoMaisGrave(porEstado))
                .contains(EstadoDeConferencia.POSSIVEL_DIVERGENCIA);
    }

    @Test
    void semProdutoNenhumNaoHaSituacao() {
        assertThat(ServicoDoHistorico.situacaoMaisGrave(zerado())).isEmpty();
    }

    private static Map<EstadoDeConferencia, Integer> zerado() {
        Map<EstadoDeConferencia, Integer> porEstado = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            porEstado.put(estado, 0);
        }
        return porEstado;
    }
}
