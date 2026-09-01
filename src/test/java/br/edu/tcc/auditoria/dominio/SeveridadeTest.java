package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.SeveridadeInvalida;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeveridadeTest {

    @Test
    void deveOrdenarDaMaisGraveParaAMenosGrave() {
        assertThat(Severidade.values()).containsExactly(
                Severidade.CRITICA,
                Severidade.GRAVE,
                Severidade.MODERADA,
                Severidade.INFORMATIVA);
    }

    @Test
    void deveCompararGravidadeEntreDoisNiveis() {
        assertThat(Severidade.CRITICA.maisGraveQue(Severidade.GRAVE)).isTrue();
        assertThat(Severidade.GRAVE.maisGraveQue(Severidade.MODERADA)).isTrue();
        assertThat(Severidade.MODERADA.maisGraveQue(Severidade.INFORMATIVA)).isTrue();
    }

    @Test
    void naoDeveConsiderarUmNivelMaisGraveQueEleMesmo() {
        assertThat(Severidade.GRAVE.maisGraveQue(Severidade.GRAVE)).isFalse();
    }

    @Test
    void naoDeveConsiderarNivelMenosGraveComoMaisGrave() {
        assertThat(Severidade.INFORMATIVA.maisGraveQue(Severidade.CRITICA)).isFalse();
    }

    @Test
    void deveRejeitarComparacaoComSeveridadeNula() {
        // O domínio não sinaliza dado inválido com exceção genérica da
        // biblioteca padrão: ver o contrato em ExcecaoDeDominio.
        assertThatThrownBy(() -> Severidade.GRAVE.maisGraveQue(null))
                .isInstanceOf(SeveridadeInvalida.class)
                .hasMessageContaining("não pode ser nula");
    }
}
