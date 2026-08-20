package br.edu.tcc.auditoria.infraestrutura.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalDeInstalacaoTest {

    private static final String DA_PROPRIEDADE = "sal-da-propriedade-de-sistema-aaaaaaaaaa";
    private static final String DO_AMBIENTE = "sal-da-variavel-de-ambiente-bbbbbbbbbbbb";

    @Test
    void devePreferirAPropriedadeDeSistemaAVariavelDeAmbiente() {
        assertThat(SalDeInstalacao.de(DA_PROPRIEDADE, DO_AMBIENTE).valor()).isEqualTo(DA_PROPRIEDADE);
    }

    @Test
    void deveUsarAVariavelDeAmbienteQuandoNaoHaPropriedadeDeSistema() {
        assertThat(SalDeInstalacao.de(null, DO_AMBIENTE).valor()).isEqualTo(DO_AMBIENTE);
    }

    @Test
    void deveIgnorarConfiguracaoEmBranco() {
        assertThat(SalDeInstalacao.de("   ", DO_AMBIENTE).valor()).isEqualTo(DO_AMBIENTE);
    }

    @Test
    void deveFalharQuandoNaoHaSalConfigurado() {
        assertThatThrownBy(() -> SalDeInstalacao.de(null, null))
                .as("sem sal configurado o sistema não pode adotar um valor padrão")
                .isInstanceOf(SalDeInstalacaoInvalido.class)
                .hasMessageContaining(SalDeInstalacao.PROPRIEDADE_DE_SISTEMA)
                .hasMessageContaining(SalDeInstalacao.VARIAVEL_DE_AMBIENTE);
    }

    @Test
    void deveRecusarSalCurtoDemais() {
        String curto = "a".repeat(SalDeInstalacao.COMPRIMENTO_MINIMO - 1);

        assertThatThrownBy(() -> new SalDeInstalacao(curto))
                .isInstanceOf(SalDeInstalacaoInvalido.class);
    }

    @Test
    void naoDeveExporOValorDoSalEmTexto() {
        assertThat(new SalDeInstalacao(DA_PROPRIEDADE).toString())
                .as("objeto de configuração costuma parar em log de inicialização")
                .doesNotContain(DA_PROPRIEDADE);
    }
}
