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

/**
 * R03 1.1.0 — o NCM do item está num dos anexos que o catálogo admite para o
 * cClassTrib de benefício, e não mais em qualquer anexo.
 *
 * <p>Decisão do usuário de 30/09/2026 (D2): a coluna {@code anexosAdmitidos}
 * lista os anexos de cada código, ou {@code NENHUM}; não declarada, a regra não
 * avalia.</p>
 *
 * <p>Todos os valores são fictícios: código {@code XXX001}, CST {@code AAA},
 * NCM zerado, anexos {@code ANEXO-XX} e {@code ANEXO-YY}. Nenhum deles é
 * afirmação sobre a legislação.</p>
 */
class RegraBeneficioExigeNcmEmAnexoAdmitidoTest {

    // Desde 01/10/2026 (D8) a cobertura é por anexo: os dois anexos fictícios são declarados carregados.
    private static final RegraBeneficioExigeNcmEmAnexo REGRA_COBERTA = new RegraBeneficioExigeNcmEmAnexo(
            CenarioFicticio.coberturaTotal().itensDeAnexo(),
            List.of(carregado(CenarioFicticio.ANEXO), carregado(CenarioFicticio.ANEXO_ALTERNATIVO)));

    @Test
    void deveApontarBeneficioSobreNcmDeAnexoQueOCodigoNaoAdmite() {
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindo(Optional.of(Set.of(CenarioFicticio.ANEXO_ALTERNATIVO))))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO));

        Avaliacao avaliacao = avaliar(catalogo);

        assertThat(avaliacao.resultado())
                .describedAs("estar em algum anexo não basta: tem de ser um anexo admitido pelo código")
                .isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.GRAVE);
    }

    @Test
    void deveDizerConformeQuandoONcmEstaNumAnexoAdmitido() {
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindo(Optional.of(
                        Set.of(CenarioFicticio.ANEXO_ALTERNATIVO, CenarioFicticio.ANEXO))))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO));

        assertThat(avaliar(catalogo).resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveDizerConformeQuandoOCodigoNaoExigeAnexo() {
        // NENHUM: o benefício não depende de anexo, e o NCM fora de anexo não é problema.
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindo(Optional.of(Set.of())))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM_ALTERNATIVO, CenarioFicticio.ANEXO));

        assertThat(avaliar(catalogo).resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNaoDeclaraOsAnexosAdmitidos() {
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(beneficioAdmitindo(Optional.empty()))
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO));

        Avaliacao avaliacao = avaliar(catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(CenarioFicticio.CODIGO)
                .contains("anexosAdmitidos");
    }

    // Classificação de benefício do código fictício com os anexos admitidos informados.
    private static ClassificacaoTributaria beneficioAdmitindo(Optional<Set<String>> anexos) {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CenarioFicticio.CODIGO),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                true,
                Optional.empty(),
                Optional.empty(),
                anexos.map(ids -> Set.copyOf(ids.stream().map(IdentificadorAnexo::new).toList())),
                List.of(),
                CenarioFicticio.procedencia());
    }

    // Anexo fictício declarado e carregado desde o início da vigência fictícia.
    private static AnexoDeclarado carregado(String anexo) {
        return new AnexoDeclarado(new IdentificadorAnexo(anexo), TipoDeCodigoDoAnexo.NCM,
                Optional.of(PeriodoVigencia.aPartirDe(CatalogoFicticio.INICIO)), "FONTE FICTICIA v0.0");
    }

    private static Avaliacao avaliar(ContextoNormativoFalso catalogo) {
        ItemDocumento item = ConstrutorDeItem.item()
                .ncm(CenarioFicticio.NCM)
                .classificacao(CenarioFicticio.CODIGO)
                .construir();
        return REGRA_COBERTA.avaliar(item, CenarioFicticio.documento(), catalogo);
    }
}
