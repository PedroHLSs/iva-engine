package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R06 — o NCM declarado consta do catálogo de NCM vigente na data? */
class RegraNcmExisteTest {

    private static final RegraNcmExiste REGRA_COBERTA =
            new RegraNcmExiste(CenarioFicticio.coberturaTotal().ncm());

    @Test
    void deveApontarNcmQueNaoConstaDoCatalogoVigente() {
        ItemDocumento item = ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.registroNcm(CenarioFicticio.NCM_ALTERNATIVO));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.CRITICA);
        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .anySatisfy(evidencia -> assertThat(evidencia.valorEncontrado()).contains(CenarioFicticio.NCM));
    }

    @Test
    void deveDizerConformeQuandoONcmConstaDoCatalogo() {
        ItemDocumento item = ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.registroNcm(CenarioFicticio.NCM));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOItemNaoDeclarouNcm() {
        Avaliacao avaliacao = REGRA_COBERTA.avaliar(
                ConstrutorDeItem.item().construir(), CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao()).isPresent();
    }

    @Test
    void naoDeveApontarQuandoATabelaCarregadaNaoAlcancaADataDeEmissao() {
        RegraNcmExiste regraSemCobertura = new RegraNcmExiste(CenarioFicticio.coberturaForaDaData().ncm());
        ItemDocumento item = ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).construir();

        Avaliacao avaliacao =
                regraSemCobertura.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.achado()).isEmpty();
    }

    @Test
    void oAchadoDeveCitarAFonteEAVigenciaDaTabelaConsultada() {
        ItemDocumento item = ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).construir();

        Avaliacao avaliacao =
                REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.achado().orElseThrow().fundamentoNormativo()).isEqualTo(CatalogoFicticio.FONTE);
        assertThat(avaliacao.achado().orElseThrow().vigenciaAplicada().fim())
                .contains(CatalogoFicticio.FIM);
    }
}
