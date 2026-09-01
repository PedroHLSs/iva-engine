package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesfechoTest {

    @Test
    void gabaritoAchadoComMotorApontandoDeveSerVerdadeiroPositivo() {
        assertThat(confrontar(RotuloEsperado.ACHADO, ResultadoAvaliacao.ACHADO))
                .isEqualTo(Desfecho.VERDADEIRO_POSITIVO);
    }

    @Test
    void gabaritoConformeComMotorApontandoDeveSerFalsoPositivo() {
        assertThat(confrontar(RotuloEsperado.CONFORME, ResultadoAvaliacao.ACHADO))
                .isEqualTo(Desfecho.FALSO_POSITIVO);
    }

    @Test
    void gabaritoAchadoComMotorConformeDeveSerFalsoNegativo() {
        assertThat(confrontar(RotuloEsperado.ACHADO, ResultadoAvaliacao.CONFORME))
                .isEqualTo(Desfecho.FALSO_NEGATIVO);
    }

    @Test
    void gabaritoConformeComMotorConformeDeveSerVerdadeiroNegativo() {
        assertThat(confrontar(RotuloEsperado.CONFORME, ResultadoAvaliacao.CONFORME))
                .isEqualTo(Desfecho.VERDADEIRO_NEGATIVO);
    }

    @Test
    void naoAvaliadoDeveSerNaoAvaliadoQualquerQueSejaORotulo() {
        assertThat(confrontar(RotuloEsperado.ACHADO, ResultadoAvaliacao.NAO_AVALIADO))
                .isEqualTo(Desfecho.NAO_AVALIADO);
        assertThat(confrontar(RotuloEsperado.CONFORME, ResultadoAvaliacao.NAO_AVALIADO))
                .isEqualTo(Desfecho.NAO_AVALIADO);
    }

    @Test
    void ausenciaDeAvaliacaoDeveSerSemAvaliacaoQualquerQueSejaORotulo() {
        assertThat(Desfecho.de(RotuloEsperado.ACHADO, Optional.empty()))
                .isEqualTo(Desfecho.SEM_AVALIACAO);
        assertThat(Desfecho.de(RotuloEsperado.CONFORME, Optional.empty()))
                .isEqualTo(Desfecho.SEM_AVALIACAO);
    }

    @Test
    void somenteOsQuatroQuadrantesDaMatrizDevemEntrarNaMetrica() {
        assertThat(Desfecho.VERDADEIRO_POSITIVO.entraNaMetrica()).isTrue();
        assertThat(Desfecho.FALSO_POSITIVO.entraNaMetrica()).isTrue();
        assertThat(Desfecho.FALSO_NEGATIVO.entraNaMetrica()).isTrue();
        assertThat(Desfecho.VERDADEIRO_NEGATIVO.entraNaMetrica()).isTrue();

        assertThat(Desfecho.NAO_AVALIADO.entraNaMetrica()).isFalse();
        assertThat(Desfecho.SEM_AVALIACAO.entraNaMetrica()).isFalse();
    }

    @Test
    void deveRecusarConfrontoSemRotuloEsperado() {
        assertThatThrownBy(() -> Desfecho.de(null, Optional.of(ResultadoAvaliacao.ACHADO)))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("gabarito");
    }

    @Test
    void deveRecusarAusenciaDeAvaliacaoRepresentadaComNulo() {
        assertThatThrownBy(() -> Desfecho.de(RotuloEsperado.ACHADO, null))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("Optional.empty()");
    }

    private static Desfecho confrontar(RotuloEsperado esperado, ResultadoAvaliacao obtido) {
        return Desfecho.de(esperado, Optional.of(obtido));
    }
}
