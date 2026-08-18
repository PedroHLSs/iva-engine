package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R02 — o par (CST declarado, {@code cClassTrib} declarado) é admitido pelo catálogo? */
class RegraCstCompativelComClassificacaoTest {

    private static final RegraCstCompativelComClassificacao REGRA = new RegraCstCompativelComClassificacao();

    @Test
    void deveApontarCstQueOCatalogoNaoAdmiteParaOCodigo() {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST_ALTERNATIVO)
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.MODERADA);
        assertThat(avaliacao.achado().orElseThrow().fundamentoNormativo())
                .isEqualTo(CatalogoFicticio.DISPOSITIVO);
    }

    @Test
    void deveDizerConformeQuandoOsCstsDeclaradosSaoAdmitidos() {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST)
                .cstCbs(CenarioFicticio.CST)
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNadaDizSobreOCodigo() {
        // Sem lista de referência não há par a julgar. Dizer conforme aqui seria
        // afirmar compatibilidade que ninguém verificou.
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST)
                .construir();

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(RegraClassificacaoTributariaExiste.ID);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNaoListaNenhumCstCompativel() {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST)
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.achado()).isEmpty();
    }

    @Test
    void naoDeveAvaliarQuandoOItemNaoDeclarouNenhumCst() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    @Test
    void deveApontarSeparadamenteOCstDeIbsEODeCbs() {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST_ALTERNATIVO)
                .cstCbs(CenarioFicticio.CST_ALTERNATIVO)
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .extracting(Evidencia::campoAnalisado)
                .contains("cstIbs", "cstCbs");
    }

    @Test
    void deveListarOsCstsAdmitidosEmOrdemEstavelNaEvidencia() {
        // O conjunto do registro não promete ordem de iteração; sem ordenar, o
        // texto da evidência mudaria entre execuções da mesma auditoria.
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST_ALTERNATIVO)
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio().com(
                CenarioFicticio.classificacao(CenarioFicticio.CODIGO, "CCC", "AAC", "ABC"));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .filteredOn(evidencia -> evidencia.campoAnalisado().equals("cstIbs"))
                .allSatisfy(evidencia -> assertThat(evidencia.valorEsperado()).contains("AAC, ABC, CCC"));
    }
}
