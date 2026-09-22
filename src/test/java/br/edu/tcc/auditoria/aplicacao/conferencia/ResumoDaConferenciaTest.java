package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.POSSIVEL_DIVERGENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.REQUER_CONFERENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O resumo da nota e do lote, e o que a precedência não consegue esconder nele. */
class ResumoDaConferenciaTest {

    @Test
    void deveContarProdutosPorSituacaoComOsQuatroNumeros() {
        ResumoDaConferencia resumo = ResumoDaConferencia.de(List.of(
                ConferenciaFicticia.produtoCom(POSSIVEL_DIVERGENCIA, SEM_DIVERGENCIA_IDENTIFICADA),
                ConferenciaFicticia.produtoCom(SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA),
                ConferenciaFicticia.produtoCom(NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA)));

        assertThat(resumo.produtosPorSituacao().porEstado()).hasSize(4);
        assertThat(resumo.produtosPorSituacao().quantidadeDe(POSSIVEL_DIVERGENCIA)).isEqualTo(1);
        assertThat(resumo.produtosPorSituacao().quantidadeDe(REQUER_CONFERENCIA)).isZero();
        assertThat(resumo.produtosPorSituacao().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isEqualTo(1);
        assertThat(resumo.produtosPorSituacao().quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isEqualTo(1);
        assertThat(resumo.quantidadeDeProdutos()).isEqualTo(3);
    }

    /**
     * O cenário que o enunciado da etapa usa: seis sem divergência e quatro não
     * concluídos não podem somar dez sem divergência em lugar nenhum.
     */
    @Test
    void naoDeveSomarProdutoNaoConcluidoAosProdutosSemDivergencia() {
        List<SituacaoDoProduto> produtos = new java.util.ArrayList<>();
        for (int contador = 0; contador < 6; contador++) {
            produtos.add(ConferenciaFicticia.produtoCom(
                    SEM_DIVERGENCIA_IDENTIFICADA, SEM_DIVERGENCIA_IDENTIFICADA));
        }
        for (int contador = 0; contador < 4; contador++) {
            produtos.add(ConferenciaFicticia.produtoCom(
                    NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA));
        }

        ResumoDaConferencia resumo = ResumoDaConferencia.de(produtos);

        assertThat(resumo.produtosPorSituacao().quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA))
                .describedAs("os quatro não concluídos não podem migrar para cá")
                .isEqualTo(6);
        assertThat(resumo.produtosPorSituacao().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isEqualTo(4);
        assertThat(resumo.produtosComAlgumaNaoConcluida()).isEqualTo(4);
    }

    @Test
    void deveContarProdutoComDivergenciaEPendenciaNasDuasContagens() {
        ResumoDaConferencia resumo = ResumoDaConferencia.de(List.of(
                ConferenciaFicticia.produtoCom(
                        POSSIVEL_DIVERGENCIA, NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA)));

        assertThat(resumo.produtosPorSituacao().quantidadeDe(POSSIVEL_DIVERGENCIA)).isEqualTo(1);
        assertThat(resumo.produtosPorSituacao().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR))
                .describedAs("a situação do produto é divergência: ele não é contado aqui")
                .isZero();
        assertThat(resumo.produtosComAlgumaNaoConcluida())
                .describedAs("mas a pendência dele não pode sumir do resumo")
                .isEqualTo(1);
        assertThat(resumo.verificacoesPorEstado().quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR))
                .describedAs("e no nível da verificação nenhuma precedência foi aplicada")
                .isEqualTo(1);
    }

    @Test
    void deveResumirAnaliseSemProdutoComOsQuatroZeros() {
        ResumoDaConferencia resumo = ResumoDaConferencia.de(List.of());

        assertThat(resumo.produtosPorSituacao().porEstado()).hasSize(4);
        assertThat(resumo.produtosPorSituacao().total()).isZero();
        assertThat(resumo.verificacoesPorEstado().total()).isZero();
        assertThat(resumo.produtosComAlgumaNaoConcluida()).isZero();
    }

    @Test
    void deveEscreverComoOsNumerosForamObtidos() {
        ResumoDaConferencia resumo = ResumoDaConferencia.de(List.of(
                ConferenciaFicticia.produtoCom(POSSIVEL_DIVERGENCIA, NAO_FOI_POSSIVEL_CONCLUIR)));

        assertThat(resumo.comoFoiObtido())
                .contains("1 produto(s)")
                .contains("2 verificação(ões)")
                .contains("não concluída");
    }

    @Test
    void deveRecusarResumoQueDigaMenosPendenciasQueProdutosNaSituacaoNaoConcluida() {
        Map<EstadoDeConferencia, Integer> porSituacao = zerada();
        porSituacao.put(NAO_FOI_POSSIVEL_CONCLUIR, 3);

        assertThatThrownBy(() -> new ResumoDaConferencia(
                new ContagemDeEstados(porSituacao),
                1,
                new ContagemDeEstados(porSituacao),
                "conta fictícia de teste"))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("ao menos uma");
    }

    @Test
    void deveRecusarResumoQueAtribuaPendenciaAProdutoSemDivergencia() {
        Map<EstadoDeConferencia, Integer> porSituacao = zerada();
        porSituacao.put(SEM_DIVERGENCIA_IDENTIFICADA, 5);

        assertThatThrownBy(() -> new ResumoDaConferencia(
                new ContagemDeEstados(porSituacao),
                2,
                new ContagemDeEstados(porSituacao),
                "conta fictícia de teste"))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("esta camada existe para impedir");
    }

    @Test
    void deveRecusarResumoComMenosVerificacoesQueProdutos() {
        Map<EstadoDeConferencia, Integer> porSituacao = zerada();
        porSituacao.put(SEM_DIVERGENCIA_IDENTIFICADA, 5);

        assertThatThrownBy(() -> new ResumoDaConferencia(
                new ContagemDeEstados(porSituacao),
                0,
                ContagemDeEstados.nenhum(),
                "conta fictícia de teste"))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("ao menos uma");
    }

    @Test
    void deveRecusarResumoSemAContaQueOProduziu() {
        assertThatThrownBy(() -> new ResumoDaConferencia(
                ContagemDeEstados.nenhum(), 0, ContagemDeEstados.nenhum(), "  "))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("como foi obtido");
    }

    private static Map<EstadoDeConferencia, Integer> zerada() {
        Map<EstadoDeConferencia, Integer> contagem = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            contagem.put(estado, 0);
        }
        return contagem;
    }
}
