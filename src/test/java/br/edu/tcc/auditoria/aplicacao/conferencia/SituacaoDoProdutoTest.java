package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.POSSIVEL_DIVERGENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.REQUER_CONFERENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Como as verificações de um produto viram uma situação, e o que a situação não pode esconder. */
class SituacaoDoProdutoTest {

    @Test
    void deveAdotarASituacaoMaisForteEntreAsVerificacoes() {
        assertThat(ConferenciaFicticia.produtoCom(
                POSSIVEL_DIVERGENCIA, REQUER_CONFERENCIA,
                NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA).situacao())
                .isEqualTo(POSSIVEL_DIVERGENCIA);

        assertThat(ConferenciaFicticia.produtoCom(
                REQUER_CONFERENCIA, NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA).situacao())
                .isEqualTo(REQUER_CONFERENCIA);

        assertThat(ConferenciaFicticia.produtoCom(
                NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA).situacao())
                .isEqualTo(NAO_FOI_POSSIVEL_CONCLUIR);
    }

    @Test
    void deveDizerSemDivergenciaSomenteQuandoTodasAsVerificacoesConcluiramSemApontamento() {
        assertThat(ConferenciaFicticia.produtoCom(
                SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA,
                SEM_DIVERGENCIA_IDENTIFICADA).situacao())
                .isEqualTo(SEM_DIVERGENCIA_IDENTIFICADA);

        // Uma única verificação de qualquer outro estado basta para impedir.
        for (EstadoDeConferencia intruso : EstadoDeConferencia.values()) {
            if (intruso == SEM_DIVERGENCIA_IDENTIFICADA) {
                continue;
            }
            assertThat(ConferenciaFicticia.produtoCom(
                    SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA, intruso).situacao())
                    .describedAs("uma verificação em \"%s\" não pode virar produto sem divergência",
                            intruso.rotulo())
                    .isNotEqualTo(SEM_DIVERGENCIA_IDENTIFICADA);
        }
    }

    @Test
    void deveManterOProdutoForaDeSemDivergenciaQuandoUmaVerificacaoNaoConcluiu() {
        SituacaoDoProduto produto = ConferenciaFicticia.produtoCom(
                SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA,
                SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA,
                SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA,
                NAO_FOI_POSSIVEL_CONCLUIR);

        assertThat(produto.situacao()).isEqualTo(NAO_FOI_POSSIVEL_CONCLUIR);
        assertThat(produto.contagens().quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isEqualTo(6);
        assertThat(produto.contagens().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isEqualTo(1);
    }

    @Test
    void deveAcusarVerificacaoNaoConcluidaAindaQuandoASituacaoForDivergencia() {
        SituacaoDoProduto produto = ConferenciaFicticia.produtoCom(
                POSSIVEL_DIVERGENCIA, NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA);

        assertThat(produto.situacao()).isEqualTo(POSSIVEL_DIVERGENCIA);
        assertThat(produto.temVerificacaoNaoConcluida())
                .describedAs("a precedência escolhe a situação, mas não apaga a pendência")
                .isTrue();
    }

    @Test
    void naoDeveAcusarPendenciaEmProdutoSemNenhumaVerificacaoNaoConcluida() {
        assertThat(ConferenciaFicticia.produtoCom(
                POSSIVEL_DIVERGENCIA, SEM_DIVERGENCIA_IDENTIFICADA).temVerificacaoNaoConcluida())
                .isFalse();
    }

    @Test
    void deveContarOsQuatroEstadosInclusiveOsZeros() {
        ContagemDeEstados contagens = ConferenciaFicticia.produtoCom(
                POSSIVEL_DIVERGENCIA, SEM_DIVERGENCIA_IDENTIFICADA).contagens();

        assertThat(contagens.porEstado()).hasSize(4);
        assertThat(contagens.quantidadeDe(POSSIVEL_DIVERGENCIA)).isEqualTo(1);
        assertThat(contagens.quantidadeDe(REQUER_CONFERENCIA)).isZero();
        assertThat(contagens.quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isZero();
        assertThat(contagens.quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isEqualTo(1);
    }

    @Test
    void deveRecusarProdutoSemNenhumaVerificacao() {
        assertThatThrownBy(() -> new SituacaoDoProduto(List.of()))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("não foi auditado");
    }

    @Test
    void deveRecusarDuasVerificacoesDaMesmaRegraSobreOMesmoProduto() {
        assertThatThrownBy(() -> new SituacaoDoProduto(List.of(
                ConferenciaFicticia.verificacao("RXX01", POSSIVEL_DIVERGENCIA),
                ConferenciaFicticia.verificacao("RXX01", SEM_DIVERGENCIA_IDENTIFICADA))))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("duas vezes");
    }

    @Test
    void deveOrdenarAsVerificacoesPeloIdentificadorDaRegra() {
        SituacaoDoProduto produto = new SituacaoDoProduto(List.of(
                ConferenciaFicticia.verificacao("RXX09", SEM_DIVERGENCIA_IDENTIFICADA),
                ConferenciaFicticia.verificacao("RXX01", SEM_DIVERGENCIA_IDENTIFICADA),
                ConferenciaFicticia.verificacao("RXX05", SEM_DIVERGENCIA_IDENTIFICADA)));

        assertThat(produto.verificacoes())
                .extracting(VerificacaoDoProduto::regraId)
                .containsExactly("RXX01", "RXX05", "RXX09");
    }

    @Test
    void deveEscreverAContaQueProduziuASituacao() {
        String conta = ConferenciaFicticia.produtoCom(
                POSSIVEL_DIVERGENCIA, NAO_FOI_POSSIVEL_CONCLUIR,
                SEM_DIVERGENCIA_IDENTIFICADA).comoFoiObtida();

        assertThat(conta).contains("3 verificação(ões)");
        assertThat(conta).contains("1 possível divergência");
        assertThat(conta).contains("0 requer conferência");
        assertThat(conta).contains("1 não foi possível concluir");
        assertThat(conta).contains("1 sem divergência identificada");
        assertThat(conta).contains(POSSIVEL_DIVERGENCIA.rotulo());
    }
}
