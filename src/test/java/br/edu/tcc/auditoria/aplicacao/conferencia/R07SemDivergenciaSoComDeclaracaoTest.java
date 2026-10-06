package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.regras.CaminhoDaR07;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D015 (03/10/2026): leva os caminhos da R07 até o estado que a tela mostra, e percorre o enum de estados. "Sem divergência identificada" só pode vir de caminho em que o catálogo declarou os campos exigidos.
class R07SemDivergenciaSoComDeclaracaoTest {

    @Test
    void semDivergenciaIdentificadaSoDeveVirDeCaminhoComDeclaracao() {
        Map<EstadoDeConferencia, Set<CaminhoDaR07>> porEstado = traduzirTodos();

        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            for (CaminhoDaR07 caminho : porEstado.get(estado)) {
                if (estado == EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA) {
                    assertThat(caminho.catalogoAfirmou())
                            .as("%s chegou a \"%s\" sem declaração no catálogo", caminho, estado.rotulo())
                            .isTrue();
                }
            }
        }
    }

    @Test
    void celulaEmBrancoDeveAparecerComoNaoFoiPossivelConcluir() {
        assertThat(TraducaoDeDesfecho.de(CaminhoDaR07.CAMPOS_NAO_DECLARADOS.avaliar()))
                .isEqualTo(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR);
    }

    // A R07 é crítica: ela produz divergência, pendência e sem divergência, e nunca "requer conferência". Os caminhos precisam alcançar os três, senão o primeiro teste olharia menos do que diz.
    @Test
    void osCaminhosDevemAlcancarOsEstadosQueAR07Produz() {
        Map<EstadoDeConferencia, Set<CaminhoDaR07>> porEstado = traduzirTodos();

        assertThat(porEstado.get(EstadoDeConferencia.POSSIVEL_DIVERGENCIA)).isNotEmpty();
        assertThat(porEstado.get(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR)).isNotEmpty();
        assertThat(porEstado.get(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA)).isNotEmpty();
        assertThat(porEstado.get(EstadoDeConferencia.REQUER_CONFERENCIA)).isEmpty();
        int contados = porEstado.values().stream().mapToInt(Set::size).sum();
        assertThat(contados).isEqualTo(CaminhoDaR07.values().length);
    }

    // Método auxiliar que roda cada caminho, traduz o desfecho no estado da tela e agrupa, com os quatro estados presentes no mapa.
    private static Map<EstadoDeConferencia, Set<CaminhoDaR07>> traduzirTodos() {
        Map<EstadoDeConferencia, Set<CaminhoDaR07>> porEstado = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            porEstado.put(estado, EnumSet.noneOf(CaminhoDaR07.class));
        }
        for (CaminhoDaR07 caminho : CaminhoDaR07.values()) {
            porEstado.get(TraducaoDeDesfecho.de(caminho.avaliar())).add(caminho);
        }
        return porEstado;
    }
}
