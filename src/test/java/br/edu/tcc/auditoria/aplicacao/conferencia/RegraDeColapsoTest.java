package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra de colapso: só a conformidade nasce recolhida.
 *
 * <p>Colapsar pendência junto com conformidade faria um lote com seis
 * conformidades e quatro não concluídos abrir parecendo limpo. Os testes percorrem
 * o enum inteiro, e não os valores de hoje: um estado novo que alguém acrescente
 * precisa nascer aberto sem que ninguém se lembre de mexer aqui.</p>
 */
class RegraDeColapsoTest {

    @Test
    void itemNaoAvaliadoNuncaNasceRecolhido() {
        assertThat(RegraDeColapso.verificacaoNasceRecolhida(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR))
                .isFalse();
    }

    @Test
    void somenteSemDivergenciaNasceRecolhidaEntreTodosOsEstados() {
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            assertThat(RegraDeColapso.verificacaoNasceRecolhida(estado))
                    .as("estado %s", estado)
                    .isEqualTo(estado == EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA);
        }
    }

    @Test
    void agrupamentoComUmaPendenciaNaoNasceRecolhidoMesmoComSeisConformidades() {
        List<EstadoDeConferencia> estados = List.of(
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR);

        assertThat(RegraDeColapso.agrupamentoNasceRecolhido(ContagemDeEstados.de(estados))).isFalse();
    }

    @Test
    void agrupamentoComQualquerEstadoQueNaoSejaSemDivergenciaNasceAberto() {
        for (EstadoDeConferencia estado : EnumSet.complementOf(
                EnumSet.of(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA))) {
            ContagemDeEstados contagem = ContagemDeEstados.de(
                    List.of(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA, estado));
            assertThat(RegraDeColapso.agrupamentoNasceRecolhido(contagem)).as("com %s dentro", estado).isFalse();
        }
    }

    @Test
    void agrupamentoInteiramenteSemDivergenciaNasceRecolhido() {
        ContagemDeEstados contagem = ContagemDeEstados.de(List.of(
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA, EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA));

        assertThat(RegraDeColapso.agrupamentoNasceRecolhido(contagem)).isTrue();
    }

    @Test
    void agrupamentoVazioNaoNasceRecolhido() {
        assertThat(RegraDeColapso.agrupamentoNasceRecolhido(ContagemDeEstados.nenhum()))
                .as("nada para mostrar não é o mesmo que nada de errado")
                .isFalse();
    }
}
