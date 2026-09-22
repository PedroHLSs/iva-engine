package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.conferencia.DescricaoDeNcm;
import br.edu.tcc.auditoria.aplicacao.conferencia.LeituraDoCatalogo;
import br.edu.tcc.auditoria.aplicacao.conferencia.ReferenciaNormativa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A descrição da nota ao lado da descrição do NCM, sob o regime opt-in.
 */
@DisplayName("Descrições lado a lado: opt-in, e nunca em branco sem motivo")
class DescricaoComparadaExpostaTest {

    private static final String DESCRICAO_DA_NOTA = "TUBO PVC FICTICIO 50MM P/ OBRA";
    private static final String DESCRICAO_DO_NCM = "DESCRICAO FICTICIA DO NCM DE TESTE";

    private static final PoliticaDeExposicao EXPOE = new PoliticaDeExposicao(false, false, true);
    private static final PoliticaDeExposicao NAO_EXPOE = PoliticaDeExposicao.restritiva();

    @Test
    void deveMostrarAsDuasQuandoAInstalacaoExpoeADaNota() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.de(Optional.of(DESCRICAO_DA_NOTA)), doCatalogo(), EXPOE);

        assertThat(bloco.naNota()).isEqualTo(DESCRICAO_DA_NOTA);
        assertThat(bloco.motivoSemDescricaoNaNota()).isNull();
        assertThat(bloco.noCatalogo()).isEqualTo(DESCRICAO_DO_NCM);
        assertThat(bloco.motivoSemDescricaoNoCatalogo()).isNull();
    }

    @Test
    void deveOmitirADaNotaPorPadraoEDizerQueFoiAConfiguracao() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.de(Optional.of(DESCRICAO_DA_NOTA)), doCatalogo(), NAO_EXPOE);

        assertThat(bloco.naNota()).isNull();
        assertThat(bloco.motivoSemDescricaoNaNota())
                .describedAs("o texto existe; quem o retém é a instalação, e a tela diz isso")
                .contains("não é exposta por esta instalação")
                .contains("auditoria.api.expor-descricao-do-produto");
        assertThat(bloco.noCatalogo())
                .describedAs("a do catálogo veio de arquivo importado de propósito, e continua")
                .isEqualTo(DESCRICAO_DO_NCM);
    }

    @Test
    void oMotivoDaPoliticaDevePrevalecerSobreOMotivoDoDocumento() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.de(Optional.empty()), doCatalogo(), NAO_EXPOE);

        assertThat(bloco.motivoSemDescricaoNaNota())
                .describedAs("dizer que o emitente não descreveu, quando foi a configuração que "
                        + "omitiu, seria pôr no documento uma falta que não é dele")
                .contains("não é exposta por esta instalação")
                .doesNotContain("o documento não declarou");
    }

    @Test
    void deveDizerQueODocumentoNaoDescreveuQuandoAInstalacaoExpoeENaoHaTexto() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.de(Optional.empty()), doCatalogo(), EXPOE);

        assertThat(bloco.naNota()).isNull();
        assertThat(bloco.motivoSemDescricaoNaNota())
                .isEqualTo(DescricaoDoProduto.NAO_VEIO_NO_DOCUMENTO);
    }

    @Test
    void deveDizerQueAAnaliseEAnteriorAoRegistroQuandoForOCaso() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.anteriorAoRegistro(), doCatalogo(), EXPOE);

        assertThat(bloco.motivoSemDescricaoNaNota())
                .describedAs("a falta é do sistema, e a frase precisa separar isso do documento")
                .contains("anterior ao registro da descrição");
    }

    @Test
    void deveDizerPorQueOCatalogoNaoDescreveONcm() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.de(Optional.of(DESCRICAO_DA_NOTA)),
                LeituraDoCatalogo.ausente("a carga fictícia não traz este NCM"),
                EXPOE);

        assertThat(bloco.noCatalogo()).isNull();
        assertThat(bloco.motivoSemDescricaoNoCatalogo())
                .isEqualTo("a carga fictícia não traz este NCM");
    }

    @Test
    void deveDizerQueNaoCompara() {
        DescricaoComparadaExposta bloco = DescricaoComparadaExposta.de(
                DescricaoDoProduto.de(Optional.of(DESCRICAO_DA_NOTA)), doCatalogo(), EXPOE);

        assertThat(bloco.comoLer())
                .describedAs("um veredito automático sobre texto não teria de onde sair")
                .contains("o sistema não as compara");
    }

    @Test
    void deveRecusarDescricaoEmBrancoSemMotivo() {
        assertThatThrownBy(() -> new DescricaoComparadaExposta(
                null, null, DESCRICAO_DO_NCM, null, DescricaoComparadaExposta.COMO_LER))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("lida como divergência");
    }

    @Test
    void deveRecusarDescricaoPresenteEOmitidaAoMesmoTempo() {
        assertThatThrownBy(() -> new DescricaoComparadaExposta(
                DESCRICAO_DA_NOTA, "motivo fictício", DESCRICAO_DO_NCM, null,
                DescricaoComparadaExposta.COMO_LER))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("exatamente um dos dois");
    }

    private static LeituraDoCatalogo<DescricaoDeNcm> doCatalogo() {
        return LeituraDoCatalogo.de(List.of(new DescricaoDeNcm(
                "00000000",
                DESCRICAO_DO_NCM,
                new ReferenciaNormativa(
                        "FONTE FICTICIA PARA TESTE", LocalDate.of(1900, 1, 1), Optional.empty()))));
    }
}
