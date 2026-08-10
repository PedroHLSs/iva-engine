package br.edu.tcc.auditoria.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultadoAvaliacaoTest {

    @Test
    void deveTerExatamenteTresDesfechos() {
        assertThat(ResultadoAvaliacao.values()).containsExactly(
                ResultadoAvaliacao.ACHADO,
                ResultadoAvaliacao.CONFORME,
                ResultadoAvaliacao.NAO_AVALIADO);
    }

    @Test
    void naoAvaliadoDeveSerDistintoDeConforme() {
        // Regra que não pôde ser aplicada não é regra que não encontrou nada.
        assertThat(ResultadoAvaliacao.NAO_AVALIADO).isNotEqualTo(ResultadoAvaliacao.CONFORME);
    }
}
