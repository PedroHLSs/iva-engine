package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aritmética das métricas, com números escolhidos para serem conferíveis à mão.
 *
 * <p>O caso de referência é VP=6, FP=2, FN=3, VN=9:</p>
 *
 * <ul>
 *   <li>precisão = 6 / (6 + 2) = 0,75;</li>
 *   <li>recall = 6 / (6 + 3) = 0,666… → 0,6667 arredondado a quatro casas;</li>
 *   <li>F1 = 2 x 6 / (2 x 6 + 2 + 3) = 12 / 17 = 0,705882… → 0,7059.</li>
 * </ul>
 */
class ContagemDeAcuraciaTest {

    @Test
    void deveCalcularPrecisaoRecallEF1ComQuatroCasasDecimais() {
        ContagemDeAcuracia contagem = new ContagemDeAcuracia(6, 2, 3, 9, 0, 0);

        assertThat(contagem.precisao().valor()).contains(new BigDecimal("0.7500"));
        assertThat(contagem.recall().valor()).contains(new BigDecimal("0.6667"));
        assertThat(contagem.f1().valor()).contains(new BigDecimal("0.7059"));
    }

    @Test
    void deveContarAvaliadosComoASomaDosQuatroQuadrantes() {
        ContagemDeAcuracia contagem = new ContagemDeAcuracia(6, 2, 3, 9, 40, 5);

        assertThat(contagem.avaliados()).isEqualTo(20);
        assertThat(contagem.total()).isEqualTo(65);
    }

    @Test
    void naoAvaliadoNaoPodeMudarPrecisaoNemRecallNemF1() {
        ContagemDeAcuracia semNaoAvaliados = new ContagemDeAcuracia(6, 2, 3, 9, 0, 0);
        ContagemDeAcuracia comMuitosNaoAvaliados = new ContagemDeAcuracia(6, 2, 3, 9, 1000, 0);

        assertThat(comMuitosNaoAvaliados.precisao()).isEqualTo(semNaoAvaliados.precisao());
        assertThat(comMuitosNaoAvaliados.recall()).isEqualTo(semNaoAvaliados.recall());
        assertThat(comMuitosNaoAvaliados.f1()).isEqualTo(semNaoAvaliados.f1());
    }

    @Test
    void linhaSemAvaliacaoCorrespondenteTambemNaoPodeMudarAsMetricas() {
        ContagemDeAcuracia semDesalinhamento = new ContagemDeAcuracia(6, 2, 3, 9, 0, 0);
        ContagemDeAcuracia comDesalinhamento = new ContagemDeAcuracia(6, 2, 3, 9, 0, 500);

        assertThat(comDesalinhamento.precisao()).isEqualTo(semDesalinhamento.precisao());
        assertThat(comDesalinhamento.recall()).isEqualTo(semDesalinhamento.recall());
        assertThat(comDesalinhamento.f1()).isEqualTo(semDesalinhamento.f1());
    }

    @Test
    void naoAvaliadoDevePuxarACoberturaParaBaixo() {
        ContagemDeAcuracia contagem = new ContagemDeAcuracia(6, 2, 3, 9, 60, 0);

        // 20 avaliados de 80 linhas de gabarito.
        assertThat(contagem.cobertura().valor()).contains(new BigDecimal("0.2500"));
    }

    @Test
    void sistemaQueNaoAvaliaNadaDeveTerPrecisaoIndefinidaENaoPrecisaoPerfeita() {
        ContagemDeAcuracia nadaAvaliado = new ContagemDeAcuracia(0, 0, 0, 0, 300, 0);

        assertThat(nadaAvaliado.precisao().estaDefinida()).isFalse();
        assertThat(nadaAvaliado.recall().estaDefinida()).isFalse();
        assertThat(nadaAvaliado.f1().estaDefinida()).isFalse();
        assertThat(nadaAvaliado.cobertura().valor()).contains(new BigDecimal("0.0000"));
    }

    @Test
    void precisaoDeveSerIndefinidaQuandoOMotorNaoApontouNadaQueFoiMedido() {
        ContagemDeAcuracia soConformes = new ContagemDeAcuracia(0, 0, 4, 10, 0, 0);

        assertThat(soConformes.precisao().estaDefinida()).isFalse();
        assertThat(soConformes.precisao().motivoDaIndefinicao()).isPresent();
        assertThat(soConformes.recall().valor()).contains(new BigDecimal("0.0000"));
    }

    @Test
    void recallDeveSerIndefinidoQuandoNenhumaLinhaMedidaFoiRotuladaComoAchado() {
        ContagemDeAcuracia soApontamentosErrados = new ContagemDeAcuracia(0, 3, 0, 10, 0, 0);

        assertThat(soApontamentosErrados.recall().estaDefinida()).isFalse();
        assertThat(soApontamentosErrados.precisao().valor()).contains(new BigDecimal("0.0000"));
    }

    @Test
    void f1DeveSerIndefinidoSempreQuePrecisaoOuRecallForem() {
        ContagemDeAcuracia semPrecisao = new ContagemDeAcuracia(0, 0, 4, 10, 0, 0);

        assertThat(semPrecisao.f1().estaDefinida()).isFalse();
        assertThat(semPrecisao.f1().motivoDaIndefinicao().orElseThrow())
                .contains("precisão")
                .contains("recall");
    }

    @Test
    void f1DeveSerZeroQuandoOMotorErrouTudoMasSePronunciouSobreTudo() {
        ContagemDeAcuracia erradoNosDoisSentidos = new ContagemDeAcuracia(0, 5, 7, 3, 0, 0);

        assertThat(erradoNosDoisSentidos.precisao().valor()).contains(new BigDecimal("0.0000"));
        assertThat(erradoNosDoisSentidos.recall().valor()).contains(new BigDecimal("0.0000"));
        assertThat(erradoNosDoisSentidos.f1().valor()).contains(new BigDecimal("0.0000"));
    }

    @Test
    void coberturaDeveSerIndefinidaQuandoOGabaritoNaoCitaARegra() {
        assertThat(ContagemDeAcuracia.nenhuma().cobertura().estaDefinida()).isFalse();
    }

    @Test
    void deveContarOsDesfechosDeUmConfrontoInteiro() {
        ContagemDeAcuracia contagem = ContagemDeAcuracia.contar(List.of(
                Desfecho.VERDADEIRO_POSITIVO,
                Desfecho.VERDADEIRO_POSITIVO,
                Desfecho.FALSO_POSITIVO,
                Desfecho.FALSO_NEGATIVO,
                Desfecho.VERDADEIRO_NEGATIVO,
                Desfecho.NAO_AVALIADO,
                Desfecho.NAO_AVALIADO,
                Desfecho.SEM_AVALIACAO));

        assertThat(contagem.verdadeirosPositivos()).isEqualTo(2);
        assertThat(contagem.falsosPositivos()).isEqualTo(1);
        assertThat(contagem.falsosNegativos()).isEqualTo(1);
        assertThat(contagem.verdadeirosNegativos()).isEqualTo(1);
        assertThat(contagem.naoAvaliados()).isEqualTo(2);
        assertThat(contagem.semAvaliacao()).isEqualTo(1);
        assertThat(contagem.total()).isEqualTo(8);
    }

    /**
     * Amarra a forma do registro à regra que {@link Desfecho#entraNaMetrica()}
     * enuncia.
     *
     * <p>Quem garante que não avaliado fique fora de precisão e recall é a forma
     * deste registro: quatro campos de matriz de confusão, dois campos à parte, e
     * fórmulas que só mencionam os quatro. {@code entraNaMetrica()} escreve essa
     * regra por extenso, e sem este teste as duas poderiam divergir em silêncio —
     * bastaria alguém acrescentar um desfecho ao enum, ou mudar
     * {@code avaliados()}, para que o texto e o comportamento discordassem.</p>
     */
    @Test
    void avaliadosDeveContarExatamenteOsDesfechosQueEntramNaMetrica() {
        for (Desfecho desfecho : Desfecho.values()) {
            ContagemDeAcuracia contagem = ContagemDeAcuracia.contar(List.of(desfecho));

            assertThat(contagem.avaliados())
                    .as("%s: avaliados tem de acompanhar entraNaMetrica()", desfecho)
                    .isEqualTo(desfecho.entraNaMetrica() ? 1 : 0);
            assertThat(contagem.total())
                    .as("%s: todo desfecho conta no total, entre na métrica ou não", desfecho)
                    .isEqualTo(1);
        }
    }

    @Test
    void deveSomarContagensParaConsolidarRegras() {
        ContagemDeAcuracia primeira = new ContagemDeAcuracia(1, 2, 3, 4, 5, 6);
        ContagemDeAcuracia segunda = new ContagemDeAcuracia(10, 20, 30, 40, 50, 60);

        assertThat(primeira.mais(segunda)).isEqualTo(new ContagemDeAcuracia(11, 22, 33, 44, 55, 66));
    }

    @Test
    void deveRecusarContagemNegativa() {
        assertThatThrownBy(() -> new ContagemDeAcuracia(-1, 0, 0, 0, 0, 0))
                .isInstanceOf(AcuraciaInvalida.class)
                .hasMessageContaining("verdadeirosPositivos");
    }

    @Test
    void deveResponderAContagemDeQualquerDesfecho() {
        ContagemDeAcuracia contagem = new ContagemDeAcuracia(1, 2, 3, 4, 5, 6);

        assertThat(contagem.quantidadeDe(Desfecho.VERDADEIRO_POSITIVO)).isEqualTo(1);
        assertThat(contagem.quantidadeDe(Desfecho.FALSO_POSITIVO)).isEqualTo(2);
        assertThat(contagem.quantidadeDe(Desfecho.FALSO_NEGATIVO)).isEqualTo(3);
        assertThat(contagem.quantidadeDe(Desfecho.VERDADEIRO_NEGATIVO)).isEqualTo(4);
        assertThat(contagem.quantidadeDe(Desfecho.NAO_AVALIADO)).isEqualTo(5);
        assertThat(contagem.quantidadeDe(Desfecho.SEM_AVALIACAO)).isEqualTo(6);
    }
}
