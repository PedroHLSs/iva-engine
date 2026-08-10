package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.PeriodoVigenciaInvalido;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoVigenciaTest {

    private static final LocalDate INICIO = DadosFicticios.INICIO_VIGENCIA;
    private static final LocalDate FIM = DadosFicticios.FIM_VIGENCIA;

    @Test
    void deveCriarVigenciaFechada() {
        PeriodoVigencia vigencia = PeriodoVigencia.de(INICIO, FIM);

        assertThat(vigencia.inicio()).isEqualTo(INICIO);
        assertThat(vigencia.fim()).contains(FIM);
        assertThat(vigencia.estaAberta()).isFalse();
    }

    @Test
    void deveCriarVigenciaAbertaSemFim() {
        PeriodoVigencia vigencia = PeriodoVigencia.aPartirDe(INICIO);

        assertThat(vigencia.fim()).isEmpty();
        assertThat(vigencia.estaAberta()).isTrue();
    }

    @Test
    void deveRejeitarInicioNulo() {
        assertThatThrownBy(() -> PeriodoVigencia.aPartirDe(null))
                .isInstanceOf(PeriodoVigenciaInvalido.class);
    }

    @Test
    void deveRejeitarFimNuloEmVezDeOptionalVazio() {
        assertThatThrownBy(() -> new PeriodoVigencia(INICIO, null))
                .isInstanceOf(PeriodoVigenciaInvalido.class);
    }

    @Test
    void deveRejeitarFimAnteriorAoInicio() {
        assertThatThrownBy(() -> PeriodoVigencia.de(FIM, INICIO))
                .isInstanceOf(PeriodoVigenciaInvalido.class);
    }

    @Test
    void deveAceitarVigenciaDeUmUnicoDia() {
        assertThat(PeriodoVigencia.de(INICIO, INICIO).contem(INICIO)).isTrue();
    }

    @Test
    void deveConsiderarOsDoisExtremosDentroDaVigencia() {
        PeriodoVigencia vigencia = PeriodoVigencia.de(INICIO, FIM);

        assertThat(vigencia.contem(INICIO)).isTrue();
        assertThat(vigencia.contem(FIM)).isTrue();
    }

    @Test
    void deveExcluirDataAnteriorAoInicio() {
        assertThat(PeriodoVigencia.de(INICIO, FIM).contem(INICIO.minusDays(1))).isFalse();
    }

    @Test
    void deveExcluirDataPosteriorAoFim() {
        assertThat(PeriodoVigencia.de(INICIO, FIM).contem(FIM.plusDays(1))).isFalse();
    }

    @Test
    void vigenciaAbertaDeveConterQualquerDataAposOInicio() {
        assertThat(PeriodoVigencia.aPartirDe(INICIO).contem(INICIO.plusYears(999))).isTrue();
    }

    @Test
    void deveRejeitarConsultaComDataNula() {
        assertThatThrownBy(() -> PeriodoVigencia.aPartirDe(INICIO).contem(null))
                .isInstanceOf(PeriodoVigenciaInvalido.class);
    }

    @Test
    void deveDistinguirVigenciaAbertaDeVigenciaFechada() {
        assertThat(PeriodoVigencia.aPartirDe(INICIO))
                .isNotEqualTo(new PeriodoVigencia(INICIO, Optional.of(FIM)));
    }
}
