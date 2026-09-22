package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O "por que este resultado" sai do que foi gravado, e não de texto por regra.
 */
@DisplayName("Passos da conferência: três procedências, três explicações")
class PassoDaConferenciaTest {

    private static final String REGRA_APONTOU = "RAA-ficticia";
    private static final String REGRA_PENDENTE = "RBB-ficticia";
    private static final String REGRA_SEM_VIOLACAO = "RCC-ficticia";

    @Test
    void deveExplicarOApontamentoPelasEvidenciasQueEleGravou() {
        PassoDaConferencia passo = passoDa(REGRA_APONTOU);

        assertThat(passo.estado()).isEqualTo(EstadoDeConferencia.POSSIVEL_DIVERGENCIA);
        assertThat(passo.explicacao()).isInstanceOf(ExplicacaoDaVerificacao.PorApontamento.class);

        ExplicacaoDaVerificacao.PorApontamento porApontamento =
                (ExplicacaoDaVerificacao.PorApontamento) passo.explicacao();

        assertThat(porApontamento.evidencias())
                .describedAs("a frase é específica deste produto porque vem da evidência dele")
                .hasSize(1);
        assertThat(porApontamento.evidencias().get(0).campoAnalisado()).isEqualTo("campoFicticio");
        assertThat(porApontamento.evidencias().get(0).valorEncontrado()).contains("99,99");
        assertThat(porApontamento.evidencias().get(0).valorEsperado()).contains("00,00");
        assertThat(porApontamento.evidencias().get(0).origem())
                .contains("derivado pela própria regra");
        assertThat(porApontamento.fundamentacao().fonteNormativa())
                .isEqualTo(ConferenciaFicticia.FUNDAMENTO);
        assertThat(porApontamento.valorEmRisco().motivoDaAusencia())
                .describedAs("valor não calculável vira motivo, nunca zero")
                .isPresent();
    }

    @Test
    void deveExplicarAPendenciaPeloMotivoQueARegraEscreveu() {
        PassoDaConferencia passo = passoDa(REGRA_PENDENTE);

        assertThat(passo.estado()).isEqualTo(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR);
        assertThat(passo.explicacao())
                .isInstanceOf(ExplicacaoDaVerificacao.PorPendencia.class);
        assertThat(((ExplicacaoDaVerificacao.PorPendencia) passo.explicacao()).motivo())
                .describedAs("o texto é o da regra, repetido sem edição")
                .isEqualTo("motivo fictício: faltou dado no cenário de teste");
    }

    @Test
    void deveExplicarOConformeDerivadoPelaContaQueOProduziu() {
        PassoDaConferencia passo = passoDa(REGRA_SEM_VIOLACAO);

        assertThat(passo.estado()).isEqualTo(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA);
        assertThat(passo.explicacao()).isInstanceOf(ExplicacaoDaVerificacao.PorDerivacao.class);

        String conta = ((ExplicacaoDaVerificacao.PorDerivacao) passo.explicacao()).conta();
        assertThat(conta)
                .contains(REGRA_SEM_VIOLACAO)
                .contains("uma avaliação por par de item e regra")
                .contains("Não é estimativa");
    }

    @Test
    void aDerivacaoNuncaDeveDizerConferido() {
        String conta = ((ExplicacaoDaVerificacao.PorDerivacao) passoDa(REGRA_SEM_VIOLACAO)
                .explicacao()).conta();

        assertThat(conta.toLowerCase())
                .describedAs("o sistema não conferiu nada: aplicou as regras que tem")
                .doesNotContain("conferido")
                .doesNotContain("conferida")
                .doesNotContain("verificado");
    }

    @Test
    void deveProduzirUmPassoPorVerificacaoNaMesmaOrdem() {
        ProdutoConferido produto = produto();

        List<PassoDaConferencia> passos = PassoDaConferencia.de(produto);

        assertThat(passos)
                .describedAs("passo faltando é regra que rodou e não aparece explicada")
                .hasSize(produto.situacao().verificacoes().size());
        assertThat(passos)
                .extracting(PassoDaConferencia::regraId)
                .containsExactlyElementsOf(produto.situacao().verificacoes().stream()
                        .map(VerificacaoDoProduto::regraId)
                        .toList());
    }

    @Test
    void aVersaoDoConformeDerivadoDeveVirComOMotivoDeNaoEstarGravada() {
        PassoDaConferencia passo = passoDa(REGRA_SEM_VIOLACAO);

        assertThat(passo.versao()).isInstanceOf(VersaoDaRegra.NaoRegistrada.class);
        assertThat(((VersaoDaRegra.NaoRegistrada) passo.versao()).motivo())
                .isNotBlank();
    }

    private static PassoDaConferencia passoDa(String regraId) {
        return PassoDaConferencia.de(produto()).stream()
                .filter(passo -> passo.regraId().equals(regraId))
                .findFirst()
                .orElseThrow();
    }

    private static ProdutoConferido produto() {
        ItemDocumento item = ConferenciaFicticia.item(1, "00000000", "FICT-001", "10.00");

        SituacaoDoProduto situacao = new SituacaoDoProduto(List.of(
                ConferenciaFicticia.verificacao(
                        REGRA_APONTOU, EstadoDeConferencia.POSSIVEL_DIVERGENCIA),
                ConferenciaFicticia.verificacao(
                        REGRA_PENDENTE, EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR),
                ConferenciaFicticia.semVersaoRegistrada(
                        REGRA_SEM_VIOLACAO, EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA)));

        return ConferenciaFicticia.produtoComRegistros(
                item,
                situacao,
                List.of(ConferenciaFicticia.registrado(REGRA_APONTOU, Severidade.GRAVE)),
                List.of(ConferenciaFicticia.pendencia(REGRA_PENDENTE, 1)));
    }
}
