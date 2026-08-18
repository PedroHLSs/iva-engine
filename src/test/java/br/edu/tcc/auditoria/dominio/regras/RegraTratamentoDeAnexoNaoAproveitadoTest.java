package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** R04 — NCM com tratamento em anexo, emitido com {@code cClassTrib} de tributação integral? */
class RegraTratamentoDeAnexoNaoAproveitadoTest {

    private static final RegraTratamentoDeAnexoNaoAproveitado REGRA_COBERTA =
            new RegraTratamentoDeAnexoNaoAproveitado(CenarioFicticio.coberturaTotal().itensDeAnexo());

    @Test
    void deveApontarNcmDeAnexoEmitidoComCodigoDeTributacaoIntegral() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO))
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    @Test
    void oApontamentoDeveSerInformativoENaoAfirmacaoDeErro() {
        // O sistema aponta, não aconselha: pode haver razão legítima para não ter
        // aproveitado o tratamento, e a severidade tem de dizer isso.
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO))
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.INFORMATIVA);
        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .extracting(Evidencia::valorEncontrado)
                .anySatisfy(valor -> assertThat(valor.orElseThrow()).contains(CatalogoFicticio.TRATAMENTO));
    }

    @Test
    void deveDizerConformeQuandoOCodigoAproveitaOTratamento() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO))
                .com(CenarioFicticio.classificacaoComBeneficio(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveDizerConformeQuandoOCodigoDeclaraReducaoAindaQueSemMarcaDeBeneficio() {
        // Redução declarada é tratamento diferenciado aplicado: não há direito ocioso.
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO))
                .com(CenarioFicticio.classificacao(
                        CenarioFicticio.CODIGO,
                        false,
                        Optional.of(new BigDecimal("99.99")),
                        List.of(),
                        CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveDizerConformeQuandoONcmNaoConstaDeAnexoAlgum() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNadaDizSobreOCodigo() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    @Test
    void naoDeveDizerConformeQuandoATabelaDeAnexosNaoAlcancaADataDeEmissao() {
        // Sem cobertura, "não consta de anexo" é falta de dado. Conformidade aqui
        // esconderia a lacuna atrás de uma linha de aparência tranquilizadora.
        RegraTratamentoDeAnexoNaoAproveitado regraSemCobertura =
                new RegraTratamentoDeAnexoNaoAproveitado(CenarioFicticio.coberturaForaDaData().itensDeAnexo());
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);

        Avaliacao avaliacao =
                regraSemCobertura.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    @Test
    void deveListarOsAnexosEmOrdemEstavelIndependentementeDaOrdemDeCarga() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO_ALTERNATIVO))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO))
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        List<String> anexosNaEvidencia = avaliacao.achado().orElseThrow().evidencias().stream()
                .filter(evidencia -> evidencia.campoAnalisado().equals("itemAnexo"))
                .map(evidencia -> evidencia.valorEncontrado().orElseThrow())
                .toList();
        assertThat(anexosNaEvidencia).isSorted();
    }

    private static ItemDocumento itemCom(String ncm, String codigo) {
        return ConstrutorDeItem.item().ncm(ncm).classificacao(codigo).construir();
    }
}
