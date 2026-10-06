package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** R03 — benefício invocado sobre NCM que o catálogo vincula a anexo? */
class RegraBeneficioExigeNcmEmAnexoTest {

    // Desde 01/10/2026 (D8) a cobertura é por anexo: o anexo fictício é declarado carregado desde o início da vigência.
    private static final List<AnexoDeclarado> ANEXO_CARREGADO = List.of(new AnexoDeclarado(
            new IdentificadorAnexo(CenarioFicticio.ANEXO), TipoDeCodigoDoAnexo.NCM,
            Optional.of(PeriodoVigencia.aPartirDe(CatalogoFicticio.INICIO)), "FONTE FICTICIA v0.0"));

    private static final RegraBeneficioExigeNcmEmAnexo REGRA_COBERTA =
            new RegraBeneficioExigeNcmEmAnexo(CenarioFicticio.coberturaTotal().itensDeAnexo(), ANEXO_CARREGADO);

    @Test
    void deveApontarBeneficioSobreNcmQueNaoConstaDeAnexo() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindoOAnexo())
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, CenarioFicticio.ANEXO));

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.GRAVE);
    }

    @Test
    void deveDizerConformeQuandoOBeneficioIncideSobreNcmDeAnexo() {
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindoOAnexo())
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
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(CenarioFicticio.CODIGO)
                .contains("nada diz sobre o cClassTrib");
    }

    @Test
    void naoDeveApontarQuandoATabelaDeAnexosNaoAlcancaADataDeEmissao() {
        RegraBeneficioExigeNcmEmAnexo regraSemCobertura =
                new RegraBeneficioExigeNcmEmAnexo(CenarioFicticio.coberturaForaDaData().itensDeAnexo(), ANEXO_CARREGADO);
        ItemDocumento item = itemCom(CenarioFicticio.NCM, CenarioFicticio.CODIGO);
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindoOAnexo());

        Avaliacao avaliacao = regraSemCobertura.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .describedAs("o motivo é a cobertura, e não outra pendência")
                .contains("não alcança a data de emissão");
        assertThat(avaliacao.achado()).isEmpty();
    }

    @Test
    void naoDeveAvaliarQuandoOItemInvocaBeneficioSemDeclararNcm() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindoOAnexo());

        Avaliacao avaliacao = REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .describedAs("o motivo é a falta de NCM, e não outra pendência")
                .contains("não declarou NCM");
    }

    // Classificação de benefício do código fictício que declara admitir só o anexo fictício (R03 1.1.0).
    private static ClassificacaoTributaria beneficioAdmitindoOAnexo() {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CenarioFicticio.CODIGO),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.of(Set.of(new IdentificadorAnexo(CenarioFicticio.ANEXO))),
                List.of(),
                CenarioFicticio.procedencia());
    }

    private static ItemDocumento itemCom(String ncm, String codigo) {
        return ConstrutorDeItem.item().ncm(ncm).classificacao(codigo).construir();
    }
}
