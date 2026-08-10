package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ValorEmRiscoInvalido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValorEmRiscoTest {

    @Test
    void deveExporAQuantiaQuandoCalculada() {
        ValorEmRisco valorEmRisco = ValorEmRisco.calculado(new BigDecimal("99.99"));

        assertThat(valorEmRisco.valor()).isPresent();
        assertThat(valorEmRisco.valor().orElseThrow()).isEqualByComparingTo("99.99");
        assertThat(valorEmRisco.motivoDaAusencia()).isEmpty();
    }

    @Test
    void deveExporOMotivoQuandoNaoCalculavel() {
        ValorEmRisco valorEmRisco = ValorEmRisco.naoCalculavel("motivo fictício para teste");

        assertThat(valorEmRisco.valor()).isEmpty();
        assertThat(valorEmRisco.motivoDaAusencia()).contains("motivo fictício para teste");
    }

    @Test
    void deveExigirMotivoQuandoNaoHaQuantia() {
        // O ponto do tipo selado: é impossível registrar ausência sem explicá-la.
        assertThatThrownBy(() -> ValorEmRisco.naoCalculavel(null))
                .isInstanceOf(ValorEmRiscoInvalido.class);
        assertThatThrownBy(() -> ValorEmRisco.naoCalculavel("   "))
                .isInstanceOf(ValorEmRiscoInvalido.class);
    }

    @Test
    void deveRejeitarQuantiaNulaEmValorCalculado() {
        assertThatThrownBy(() -> ValorEmRisco.calculado(null))
                .isInstanceOf(ValorEmRiscoInvalido.class)
                .hasMessageContaining("naoCalculavel");
    }

    @Test
    void deveDistinguirQuantiaZeroDeQuantiaNaoCalculavel() {
        // Risco aferido em zero é conclusão; risco não calculável é ausência de conclusão.
        ValorEmRisco zero = ValorEmRisco.calculado(new BigDecimal("0.00"));
        ValorEmRisco semQuantia = ValorEmRisco.naoCalculavel("motivo fictício para teste");

        assertThat(zero.valor()).isPresent();
        assertThat(semQuantia.valor()).isEmpty();
        assertThat(zero).isNotEqualTo(semQuantia);
    }

    @Test
    void devePermitirDecidirPorPatternMatching() {
        String descricao = switch (ValorEmRisco.naoCalculavel("motivo fictício para teste")) {
            case ValorEmRisco.Calculado calculado -> calculado.quantia().toPlainString();
            case ValorEmRisco.NaoCalculavel naoCalculavel -> naoCalculavel.motivo();
        };

        assertThat(descricao).isEqualTo("motivo fictício para teste");
    }
}
