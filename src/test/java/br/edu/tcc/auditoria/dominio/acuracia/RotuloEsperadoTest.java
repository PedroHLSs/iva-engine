package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.RotuloEsperadoInvalido;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RotuloEsperadoTest {

    @Test
    void deveLerOsDoisRotulosAceitos() {
        assertThat(RotuloEsperado.de("ACHADO")).isEqualTo(RotuloEsperado.ACHADO);
        assertThat(RotuloEsperado.de("CONFORME")).isEqualTo(RotuloEsperado.CONFORME);
    }

    @Test
    void deveAceitarMinusculasEEspacosEmVolta() {
        assertThat(RotuloEsperado.de("  achado  ")).isEqualTo(RotuloEsperado.ACHADO);
    }

    @Test
    void deveRecusarNaoAvaliadoExplicandoQueEleNaoERotuloDeGabarito() {
        assertThatThrownBy(() -> RotuloEsperado.de("NAO_AVALIADO"))
                .isInstanceOf(RotuloEsperadoInvalido.class)
                .hasMessageContaining("desfecho do sistema")
                .hasMessageContaining("cobertura");
    }

    @Test
    void deveRecusarRotuloDesconhecidoListandoOsAceitos() {
        assertThatThrownBy(() -> RotuloEsperado.de("TALVEZ"))
                .isInstanceOf(RotuloEsperadoInvalido.class)
                .hasMessageContaining("TALVEZ")
                .hasMessageContaining("ACHADO, CONFORME");
    }

    @Test
    void deveRecusarAbreviacao() {
        assertThatThrownBy(() -> RotuloEsperado.de("CONF"))
                .isInstanceOf(RotuloEsperadoInvalido.class);
    }

    @Test
    void deveRecusarRotuloEmBranco() {
        assertThatThrownBy(() -> RotuloEsperado.de("   "))
                .isInstanceOf(RotuloEsperadoInvalido.class)
                .hasMessageContaining("obrigatório");
    }
}
