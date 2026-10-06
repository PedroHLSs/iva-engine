package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R03 1.1.0 com cobertura por anexo (decisões do usuário D7, D8 e D9, de
 * 01/10/2026).
 *
 * <p>Um anexo está carregado numa data só se a carga o declara com período e a
 * data cai nele. CONFORME se o NCM está num anexo admitido e carregado; ACHADO só
 * se todos os admitidos estão carregados e o NCM não está em nenhum; qualquer
 * outro caso é NAO_AVALIADO, com o motivo "anexo admitido não carregado".</p>
 *
 * <p>Todos os valores são fictícios: código {@code XXX001}, CST {@code AAA}, NCM
 * zerado, anexos {@code ANEXO-XX}, {@code ANEXO-YY} e {@code ANEXO-ZZ}, datas em
 * 1900. Nenhum deles é afirmação sobre a legislação.</p>
 */
class CoberturaPorAnexoNaR03Test {

    private static final String ANEXO_XX = CenarioFicticio.ANEXO;
    private static final String ANEXO_YY = CenarioFicticio.ANEXO_ALTERNATIVO;
    private static final String ANEXO_ZZ = "ANEXO-ZZ";

    private static final String MOTIVO = "anexo admitido não carregado";

    @Test
    void deveDizerConformeQuandoONcmEstaNumAnexoAdmitidoECarregado() {
        Avaliacao avaliacao = avaliar(
                List.of(carregado(ANEXO_XX)),
                admitindo(ANEXO_XX),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM, ANEXO_XX));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveApontarQuandoTodosOsAdmitidosEstaoCarregadosEONcmNaoEstaEmNenhum() {
        Avaliacao avaliacao = avaliar(
                List.of(carregado(ANEXO_XX), carregado(ANEXO_YY)),
                admitindo(ANEXO_XX, ANEXO_YY),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, ANEXO_XX));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    @Test
    void naoDeveApontarQuandoSoParteDosAdmitidosEstaCarregadaEONcmNaoEstaNela() {
        // Cobertura parcial: o NCM pode estar no anexo que não foi carregado.
        Avaliacao avaliacao = avaliar(
                List.of(carregado(ANEXO_XX), declaradoSemPeriodo(ANEXO_YY)),
                admitindo(ANEXO_XX, ANEXO_YY),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, ANEXO_XX));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(MOTIVO + " [" + ANEXO_YY + "]");
    }

    @Test
    void naoDeveAvaliarQuandoNenhumAdmitidoEstaCarregado() {
        Avaliacao avaliacao = avaliar(
                List.of(declaradoSemPeriodo(ANEXO_XX)),
                admitindo(ANEXO_XX),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, ANEXO_YY));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(MOTIVO + " [" + ANEXO_XX + "]");
    }

    @Test
    void naoDeveAvaliarQuandoOPeriodoDoAnexoNaoAlcancaADataDaNota() {
        AnexoDeclarado foraDaData = new AnexoDeclarado(new IdentificadorAnexo(ANEXO_XX), TipoDeCodigoDoAnexo.NCM,
                Optional.of(PeriodoVigencia.aPartirDe(CatalogoFicticio.INICIO_SEGUINTE)), "FONTE FICTICIA v0.0");

        Avaliacao avaliacao = avaliar(
                List.of(foraDaData),
                admitindo(ANEXO_XX),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, ANEXO_YY));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(MOTIVO + " [" + ANEXO_XX + "]");
    }

    @Test
    void naoDeveDizerConformeQuandoONcmEstaNumAdmitidoQueNaoEstaCarregado() {
        // D8 ao pé da letra: CONFORME exige anexo admitido e carregado.
        Avaliacao avaliacao = avaliar(
                List.of(declaradoSemPeriodo(ANEXO_XX)),
                admitindo(ANEXO_XX),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM, ANEXO_XX));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(MOTIVO + " [" + ANEXO_XX + "]");
    }

    @Test
    void deveApontarQualquerNcmQuandoOAnexoAdmitidoEstaCarregadoEVazio() {
        // D7: anexo NBS carregado sem nenhuma linha em item-anexo é carregado e vazio de NCM, não "não carregado".
        AnexoDeclarado vazioDeNcm = new AnexoDeclarado(new IdentificadorAnexo(ANEXO_ZZ), TipoDeCodigoDoAnexo.NBS,
                Optional.of(PeriodoVigencia.aPartirDe(CatalogoFicticio.INICIO)), "FONTE FICTICIA v0.0");

        Avaliacao avaliacao = avaliar(
                List.of(vazioDeNcm),
                admitindo(ANEXO_ZZ),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM, ANEXO_XX));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    @Test
    void deveDizerConformeQuandoOCodigoNaoExigeAnexoMesmoSemAnexoDeclarado() {
        Avaliacao avaliacao = avaliar(
                List.of(),
                admitindo(),
                CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, ANEXO_XX));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void cargaAntigaSemCoberturaPorAnexoNaoDeveSerAvaliada() {
        // Carga gravada antes de 01/10/2026: a regra é construída sem anexos declarados.
        RegraBeneficioExigeNcmEmAnexo semDeclaracao =
                new RegraBeneficioExigeNcmEmAnexo(CenarioFicticio.coberturaTotal().itensDeAnexo());
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(admitindo(ANEXO_XX))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, ANEXO_XX));

        Avaliacao avaliacao = semDeclaracao.avaliar(item(), CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(MOTIVO + " [" + ANEXO_XX + "]");
    }

    @Test
    void deveManterOMotivoDeCoberturaDaTabelaForaDaData() {
        RegraBeneficioExigeNcmEmAnexo foraDaData = new RegraBeneficioExigeNcmEmAnexo(
                CenarioFicticio.coberturaForaDaData().itensDeAnexo(), List.of(carregado(ANEXO_XX)));
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio().com(admitindo(ANEXO_XX));

        Avaliacao avaliacao = foraDaData.avaliar(item(), CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains("não alcança a data de emissão");
    }

    // Anexo declarado e carregado desde o início da vigência fictícia.
    private static AnexoDeclarado carregado(String anexo) {
        return new AnexoDeclarado(new IdentificadorAnexo(anexo), TipoDeCodigoDoAnexo.NCM,
                Optional.of(PeriodoVigencia.aPartirDe(CatalogoFicticio.INICIO)), "FONTE FICTICIA v0.0");
    }

    // Anexo declarado sem período: existe, mas não está carregado.
    private static AnexoDeclarado declaradoSemPeriodo(String anexo) {
        return new AnexoDeclarado(new IdentificadorAnexo(anexo), TipoDeCodigoDoAnexo.NCM,
                Optional.empty(), "FONTE FICTICIA v0.0");
    }

    // Classificação de benefício do código fictício admitindo os anexos informados; sem nenhum, é NENHUM.
    private static ClassificacaoTributaria admitindo(String... anexos) {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CenarioFicticio.CODIGO),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.of(Arrays.stream(anexos).map(IdentificadorAnexo::new).collect(Collectors.toSet())),
                List.of(),
                CenarioFicticio.procedencia());
    }

    private static ItemDocumento item() {
        return ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).classificacao(CenarioFicticio.CODIGO).construir();
    }

    private static Avaliacao avaliar(
            List<AnexoDeclarado> declarados,
            ClassificacaoTributaria classificacao,
            ItemAnexo itemAnexo) {
        RegraBeneficioExigeNcmEmAnexo regra =
                new RegraBeneficioExigeNcmEmAnexo(CenarioFicticio.coberturaTotal().itensDeAnexo(), declarados);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio().com(classificacao).com(itemAnexo);
        return regra.avaliar(item(), CenarioFicticio.documento(), catalogo);
    }
}
