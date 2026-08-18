package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** R07 — os campos que o catálogo condiciona ao {@code cClassTrib} vieram preenchidos? */
class RegraCamposObrigatoriosPreenchidosTest {

    private static final RegraCamposObrigatoriosPreenchidos REGRA = new RegraCamposObrigatoriosPreenchidos();

    private static final String BASE_IBS = CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo();
    private static final String VALOR_CBS = CampoDoItem.VALOR_CBS.nomeNoCatalogo();

    @Test
    void deveApontarCampoExigidoPeloCatalogoENaoInformadoNoItem() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        ContextoNormativoFalso catalogo = catalogoExigindo(List.of(BASE_IBS));

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.CRITICA);
        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .filteredOn(evidencia -> evidencia.campoAnalisado().equals(BASE_IBS))
                .singleElement()
                .satisfies(evidencia -> assertThat(evidencia.valorEncontrado()).isEmpty());
    }

    @Test
    void deveDizerConformeQuandoOsCamposExigidosVieram() {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs("99.99")
                .construir();

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogoExigindo(List.of(BASE_IBS)));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveTratarCampoDeclaradoComoZeroComoPreenchido() {
        // Zero é informação prestada; ausência é omissão. Confundir os dois
        // apagaria justamente o que esta regra existe para detectar.
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs("0.00")
                .construir();

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogoExigindo(List.of(BASE_IBS)));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNadaDizSobreOCodigo() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.achado()).isEmpty();
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoExigeCampoQueOSistemaNaoSabeLer() {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs("99.99")
                .construir();

        Avaliacao avaliacao = REGRA.avaliar(
                item, CenarioFicticio.documento(), catalogoExigindo(List.of(BASE_IBS, "campoInexistenteXX")));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains("campoInexistenteXX");
    }

    @Test
    void deveApontarOCampoAusenteERegistrarONomeNaoReconhecidoNaEvidencia() {
        // Nada desaparece: a falta vira achado e o nome que o sistema não sabe ler
        // vai junto, para que a lacuna não fique invisível no relatório.
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA.avaliar(
                item, CenarioFicticio.documento(), catalogoExigindo(List.of(BASE_IBS, "campoInexistenteXX")));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .extracting(Evidencia::valorEncontrado)
                .anySatisfy(valor -> assertThat(valor).contains("campoInexistenteXX"));
    }

    @Test
    void deveDizerConformeQuandoOCatalogoNaoCondicionaCampoAlgum() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA.avaliar(item, CenarioFicticio.documento(), catalogoExigindo(List.of()));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOItemNaoDeclarouCodigo() {
        Avaliacao avaliacao = REGRA.avaliar(
                ConstrutorDeItem.item().construir(),
                CenarioFicticio.documento(),
                catalogoExigindo(List.of(BASE_IBS)));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    @Test
    void deveApontarTodosOsCamposExigidosQueFaltaram() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();

        Avaliacao avaliacao = REGRA.avaliar(
                item, CenarioFicticio.documento(), catalogoExigindo(List.of(BASE_IBS, VALOR_CBS)));

        assertThat(avaliacao.achado().orElseThrow().evidencias())
                .extracting(Evidencia::campoAnalisado)
                .contains(BASE_IBS, VALOR_CBS);
    }

    private static ContextoNormativoFalso catalogoExigindo(List<String> camposExigidos) {
        return ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacaoExigindo(CenarioFicticio.CODIGO, camposExigidos));
    }
}
