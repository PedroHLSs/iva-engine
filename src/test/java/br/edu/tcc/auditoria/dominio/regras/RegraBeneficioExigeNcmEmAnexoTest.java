package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R03 — benefício invocado sobre NCM que o catálogo vincula a anexo? */
class RegraBeneficioExigeNcmEmAnexoTest {

    private static final RegraBeneficioExigeNcmEmAnexo REGRA_COBERTA =
            new RegraBeneficioExigeNcmEmAnexo(CenarioFicticio.coberturaTotal().itensDeAnexo());

    @Test
    void deveApontarBeneficioSobreNcmQueNaoConstaDeAnexo() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacaoComBeneficio(CenarioFicticio.CODIGO, CenarioFicticio.CST))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, CenarioFicticio.ANEXO));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.GRAVE);
    }

    @Test
    void deveDizerConformeQuandoOBeneficioIncideSobreNcmDeAnexo() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacaoComBeneficio(CenarioFicticio.CODIGO, CenarioFicticio.CST))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveDizerConformeQuandoOCodigoNaoIndicaBeneficio() {
        // A exigência de anexo não se aplica: a regra se esgota tendo sido
        // aplicada por inteiro, e isso é conformidade, não falta de dado.
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNadaDizSobreOCodigo() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);

        Avaliacao avaliacao =
                REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(CenarioFicticio.CODIGO);
    }

    @Test
    void naoDeveApontarQuandoATabelaDeAnexosNaoAlcancaADataDeEmissao() {
        RegraBeneficioExigeNcmEmAnexo regraSemCobertura =
                new RegraBeneficioExigeNcmEmAnexo(CenarioFicticio.coberturaForaDaData().itensDeAnexo());
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacaoComBeneficio(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = regraSemCobertura.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.achado()).isEmpty();
    }

    @Test
    void naoDeveAvaliarQuandoOItemInvocaBeneficioSemDeclararNcm() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacaoComBeneficio(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    private static ItemDocumento itemCom(String ncm, String codigo) {
        return ConstrutorDeItem.item().ncm(ncm).classificacao(codigo).construir();
    }
}
