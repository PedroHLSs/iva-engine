package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A folga que a conferência de valor admite. */
class ToleranciaDeValorTest {

    @Test
    void deveRecusarToleranciaNegativa() {
        assertThatThrownBy(() -> ToleranciaDeValor.de(new BigDecimal("-0.01")))
                .isInstanceOf(RegraInvalida.class);
    }

    @Test
    void deveAcomodarDiferencaIgualAoLimite() {
        ToleranciaDeValor tolerancia = ToleranciaDeValor.de(new BigDecimal("0.01"));

        assertThat(tolerancia.acomoda(new BigDecimal("0.01"))).isTrue();
        assertThat(tolerancia.acomoda(new BigDecimal("-0.01"))).isTrue();
        assertThat(tolerancia.acomoda(new BigDecimal("0.02"))).isFalse();
    }

    @Test
    void deveCompararPorGrandezaENaoPelaEscalaDeclarada() {
        // "0" e "0,00" são registros diferentes do mesmo número; aqui interessa
        // o número.
        assertThat(ToleranciaDeValor.exata().acomoda(new BigDecimal("0.0000"))).isTrue();
        assertThat(ToleranciaDeValor.de(new BigDecimal("0.10")).acomoda(new BigDecimal("0.1"))).isTrue();
    }

    @Test
    void toleranciaExataDeveExigirIgualdadeDeGrandeza() {
        assertThat(ToleranciaDeValor.exata().acomoda(new BigDecimal("0.01"))).isFalse();
    }
}
