package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Bloco do catálogo vazio sempre diz por quê")
class LeituraDoCatalogoTest {

    @Test
    void deveRecusarBlocoVazioSemMotivo() {
        assertThatThrownBy(() -> new LeituraDoCatalogo<String>(List.of(), Optional.empty()))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("não respondeu e não foi dito por quê");
    }

    @Test
    void deveRecusarConteudoComMotivoDeAusenciaAoMesmoTempo() {
        assertThatThrownBy(() ->
                new LeituraDoCatalogo<>(List.of("algo"), Optional.of("motivo fictício")))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("não podem valer juntas");
    }

    @Test
    void deveRecusarMotivoEmBranco() {
        assertThatThrownBy(() -> LeituraDoCatalogo.ausente("   "))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("não pode ser texto em branco");
    }

    @Test
    void deveTratarRespostaUnicaAusenteComoAusenciaExplicada() {
        LeituraDoCatalogo<String> leitura =
                LeituraDoCatalogo.deUnico(Optional.empty(), "motivo fictício de teste");

        assertThat(leitura.respondido()).isFalse();
        assertThat(leitura.quantidade()).isZero();
        assertThat(leitura.motivoDaAusencia()).contains("motivo fictício de teste");
    }

    @Test
    void deveTratarRespostaUnicaPresenteComoRespondidaSemMotivo() {
        LeituraDoCatalogo<String> leitura =
                LeituraDoCatalogo.deUnico(Optional.of("conteúdo"), "motivo que não deve aparecer");

        assertThat(leitura.respondido()).isTrue();
        assertThat(leitura.encontrado()).containsExactly("conteúdo");
        assertThat(leitura.motivoDaAusencia()).isEmpty();
    }

    @Test
    void deveRecusarElementoNuloNaLista() {
        List<String> comNulo = new java.util.ArrayList<>();
        comNulo.add("presente");
        comNulo.add(null);

        assertThatThrownBy(() -> LeituraDoCatalogo.de(comNulo))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("não pode conter elemento nulo");
    }
}
