package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerieNormativaTest {

    @Test
    void deveAceitarVersoesEmSequenciaSemSobreposicao() {
        SerieNormativa<ClassificacaoTributaria> serie = SerieNormativa.de(List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE))));

        assertThat(serie.quantidadeDeVersoes()).isEqualTo(2);
        assertThat(serie.chave()).isEqualTo(CatalogoFicticio.CODIGO);
    }

    @Test
    void deveOrdenarAsVersoesPorInicioDeVigencia() {
        SerieNormativa<ClassificacaoTributaria> serie = SerieNormativa.de(List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));

        assertThat(serie.versoes().get(0).vigenciaInicio()).isEqualTo(CatalogoFicticio.INICIO);
        assertThat(serie.versoes().get(1).vigenciaInicio()).isEqualTo(CatalogoFicticio.INICIO_SEGUINTE);
    }

    @Test
    void deveRejeitarDuasVersoesComVigenciasSobrepostas() {
        List<ClassificacaoTributaria> sobrepostas = List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.INICIO_SEGUINTE)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM)));

        assertThatThrownBy(() -> SerieNormativa.de(sobrepostas))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("sobrepostas")
                .hasMessageContaining(CatalogoFicticio.CODIGO);
    }

    @Test
    void deveRejeitarSobreposicaoQuandoAVersaoAnteriorTemVigenciaAberta() {
        // Vigência aberta engole tudo o que começa depois dela.
        List<ClassificacaoTributaria> sobrepostas = List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE)));

        assertThatThrownBy(() -> SerieNormativa.de(sobrepostas))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void deveRejeitarDuasVersoesComOMesmoInicioDeVigencia() {
        List<ClassificacaoTributaria> sobrepostas = List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)));

        assertThatThrownBy(() -> SerieNormativa.de(sobrepostas))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void deveAceitarVigenciasQueSeEncostamSemSobrepor() {
        // Fim num dia e início no dia seguinte não é sobreposição.
        SerieNormativa<ClassificacaoTributaria> serie = SerieNormativa.de(List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM.plusDays(1)))));

        assertThat(serie.quantidadeDeVersoes()).isEqualTo(2);
    }

    @Test
    void deveRejeitarVersoesDeChavesDiferentesNaMesmaSerie() {
        assertThatThrownBy(() -> new SerieNormativa<>(CatalogoFicticio.CODIGO, List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO_ALTERNATIVO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)))))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining(CatalogoFicticio.CODIGO_ALTERNATIVO);
    }

    @Test
    void deveRejeitarSerieSemNenhumaVersao() {
        assertThatThrownBy(() -> SerieNormativa.de(List.<ClassificacaoTributaria>of()))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void deveResolverAVersaoVigenteNaData() {
        SerieNormativa<ClassificacaoTributaria> serie = serieDeDuasVersoes();

        assertThat(serie.vigenteEm(CatalogoFicticio.INICIO)).isPresent();
        assertThat(serie.vigenteEm(CatalogoFicticio.INICIO).orElseThrow().vigenciaFim())
                .contains(CatalogoFicticio.FIM);
        assertThat(serie.vigenteEm(CatalogoFicticio.INICIO_SEGUINTE).orElseThrow().vigenciaFim())
                .isEmpty();
    }

    @Test
    void naoDeveRetornarVersaoEmDataAnteriorAoInicioDaVigencia() {
        SerieNormativa<ClassificacaoTributaria> serie = serieDeDuasVersoes();

        assertThat(serie.vigenteEm(CatalogoFicticio.INICIO.minusDays(1))).isEmpty();
    }

    @Test
    void naoDeveRetornarVersaoEmDataPosteriorAoFimDaVigencia() {
        SerieNormativa<ClassificacaoTributaria> serie = SerieNormativa.de(List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));

        assertThat(serie.vigenteEm(CatalogoFicticio.FIM)).isPresent();
        assertThat(serie.vigenteEm(CatalogoFicticio.FIM.plusDays(1))).isEmpty();
    }

    @Test
    void naoDeveRetornarVersaoEmDataCaidaEmIntervaloDescoberto() {
        // Buraco proposital entre as duas vigências: o catálogo nada diz ali,
        // e a resposta correta é vazio, não a versão mais próxima.
        SerieNormativa<ClassificacaoTributaria> serie = SerieNormativa.de(List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM.plusDays(10)))));

        assertThat(serie.vigenteEm(CatalogoFicticio.FIM.plusDays(5))).isEmpty();
    }

    @Test
    void deveRejeitarConsultaComDataNula() {
        assertThatThrownBy(() -> serieDeDuasVersoes().vigenteEm(null))
                .isInstanceOf(CatalogoInvalido.class);
    }

    private static SerieNormativa<ClassificacaoTributaria> serieDeDuasVersoes() {
        return SerieNormativa.de(List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE))));
    }
}
