package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R01 — o {@code cClassTrib} declarado consta do catálogo vigente na data? */
class RegraClassificacaoTributariaExisteTest {

    private static final RegraClassificacaoTributariaExiste REGRA_COBERTA =
            new RegraClassificacaoTributariaExiste(CenarioFicticio.coberturaTotal().classificacoesTributarias());

    @Test
    void deveApontarCodigoQueNaoConstaDoCatalogoVigente() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO_ALTERNATIVO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado()).isPresent();
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.CRITICA);
    }

    @Test
    void deveDizerConformeQuandoOCodigoConstaDoCatalogo() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
        assertThat(avaliacao.achado()).isEmpty();
        assertThat(avaliacao.motivoDaNaoAvaliacao()).isEmpty();
    }

    @Test
    void naoDeveAvaliarQuandoOItemNaoDeclarouCodigo() {
        Avaliacao avaliacao = REGRA_COBERTA.avaliar(
                ConstrutorDeItem.item().construir(), CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao()).isPresent();
    }

    @Test
    void naoDeveApontarQuandoATabelaCarregadaNaoAlcancaADataDeEmissao() {
        // A regra de ouro em sua forma mais perigosa: com o catálogo mudo e sem
        // cobertura, apontar seria acusar o documento por falta de carga.
        RegraClassificacaoTributariaExiste regraSemCobertura = new RegraClassificacaoTributariaExiste(
                CenarioFicticio.coberturaForaDaData().classificacoesTributarias());
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao =
                regraSemCobertura.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(CenarioFicticio.DATA_EMISSAO.toString());
    }

    @Test
    void oAchadoDeveCitarAFonteEAVigenciaDaTabelaConsultada() {
        // Não há registro de onde tomar fundamento e vigência — a inexistência do
        // registro é o próprio achado —, então os dois vêm da cobertura declarada.
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(
                item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.achado().orElseThrow().fundamentoNormativo()).isEqualTo(CatalogoFicticio.FONTE);
        assertThat(avaliacao.achado().orElseThrow().vigenciaAplicada().inicio())
                .isEqualTo(CatalogoFicticio.INICIO);
    }

    @Test
    void oAchadoDeveRegistrarOCodigoDeclaradoComoEvidencia() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(
                item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .anySatisfy(evidencia -> assertThat(evidencia.valorEncontrado()).contains(CenarioFicticio.CODIGO));
    }

    @Test
    void oAchadoNaoDeveTerValorEmRiscoCalculavel() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(
                item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.achado().orElseThrow().quantiaEmRisco()).isEmpty();
        assertThat(avaliacao.achado().orElseThrow().valorEmRisco().motivoDaAusencia()).isPresent();
    }
}
