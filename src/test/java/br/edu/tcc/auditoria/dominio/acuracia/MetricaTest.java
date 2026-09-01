package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricaTest {

    @Test
    void deveArredondarAQuatroCasasDecimais() {
        Metrica metrica = Metrica.definida(new BigDecimal("0.66666666"));

        assertThat(metrica.valor()).contains(new BigDecimal("0.6667"));
    }

    @Test
    void deveGuardarAEscalaMesmoQuandoOValorEExato() {
        assertThat(Metrica.definida(BigDecimal.ONE).valor()).contains(new BigDecimal("1.0000"));
        assertThat(Metrica.definida(BigDecimal.ZERO).valor()).contains(new BigDecimal("0.0000"));
    }

    @Test
    void razaoComDenominadorZeroDeveSerIndefinidaComOMotivo() {
        Metrica metrica = Metrica.razao(0, 0, "não há denominador fictício");

        assertThat(metrica.estaDefinida()).isFalse();
        assertThat(metrica.valor()).isEmpty();
        assertThat(metrica.motivoDaIndefinicao()).contains("não há denominador fictício");
    }

    @Test
    void razaoComDenominadorZeroNaoPodeValerZeroNemUm() {
        Metrica indefinida = Metrica.razao(0, 0, "sem denominador");

        assertThat(indefinida).isNotEqualTo(Metrica.definida(BigDecimal.ZERO));
        assertThat(indefinida).isNotEqualTo(Metrica.definida(BigDecimal.ONE));
    }

    @Test
    void deveCalcularARazaoQuandoHaDenominador() {
        assertThat(Metrica.razao(3, 4, "irrelevante").valor()).contains(new BigDecimal("0.7500"));
    }

    @Test
    void metricaIndefinidaPrecisaDoMotivo() {
        assertThatThrownBy(() -> Metrica.indefinida("  "))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("por que não há valor");
    }

    @Test
    void metricaDefinidaPrecisaDoValor() {
        assertThatThrownBy(() -> Metrica.definida(null))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("Metrica.indefinida");
    }

    @Test
    void deveRecusarProporcaoForaDeZeroAUm() {
        assertThatThrownBy(() -> Metrica.definida(new BigDecimal("1.5")))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("erro de contagem");
    }

    @Test
    void deveRecusarRazaoComContagemNegativa() {
        assertThatThrownBy(() -> Metrica.razao(-1, 10, "irrelevante"))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("negativa");
    }
}
