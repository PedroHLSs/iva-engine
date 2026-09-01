package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.CHAVE;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.CONJUNTO;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.OUTRA_CHAVE;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.REGRA_PRIMEIRA;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.REGRA_SEGUNDA;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.VERSAO_CATALOGO;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.VERSAO_CONJUNTO;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.avaliacao;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.avaliacaoDeDocumento;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.endereco;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.linha;
import static br.edu.tcc.auditoria.dominio.ResultadoAvaliacao.ACHADO;
import static br.edu.tcc.auditoria.dominio.ResultadoAvaliacao.CONFORME;
import static br.edu.tcc.auditoria.dominio.ResultadoAvaliacao.NAO_AVALIADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O confronto entre gabarito e motor, com casos conferíveis à mão.
 *
 * <p>O cenário base tem oito linhas de gabarito na regra {@code RX1}, montadas
 * para dar VP=3, FP=1, FN=2, VN=2 — de onde precisão = 3/4 = 0,75, recall =
 * 3/5 = 0,6 e F1 = 2x3 / (2x3 + 1 + 2) = 6/9 = 0,6667.</p>
 */
class ComparadorDeGabaritoTest {

    private final ComparadorDeGabarito comparador = new ComparadorDeGabarito();

    @Test
    void deveCalcularAsMetricasDeUmCenarioConferivelAMao() {
        ContagemDeAcuracia contagem = cenarioBase().medir()
                .daRegra(REGRA_PRIMEIRA).orElseThrow().contagem();

        assertThat(contagem).isEqualTo(new ContagemDeAcuracia(3, 1, 2, 2, 0, 0));
        assertThat(contagem.precisao().valor()).contains(new BigDecimal("0.7500"));
        assertThat(contagem.recall().valor()).contains(new BigDecimal("0.6000"));
        assertThat(contagem.f1().valor()).contains(new BigDecimal("0.6667"));
        assertThat(contagem.cobertura().valor()).contains(new BigDecimal("1.0000"));
    }

    @Test
    void naoAvaliadoNaoPodeAlterarPrecisaoRecallNemF1() {
        Montagem comNaoAvaliados = cenarioBase();
        for (int item = 9; item <= 18; item++) {
            comNaoAvaliados.caso(item, RotuloEsperado.ACHADO, NAO_AVALIADO);
        }

        ContagemDeAcuracia sem = cenarioBase().medir().daRegra(REGRA_PRIMEIRA).orElseThrow().contagem();
        ContagemDeAcuracia com = comNaoAvaliados.medir().daRegra(REGRA_PRIMEIRA).orElseThrow().contagem();

        assertThat(com.precisao()).isEqualTo(sem.precisao());
        assertThat(com.recall()).isEqualTo(sem.recall());
        assertThat(com.f1()).isEqualTo(sem.f1());

        assertThat(com.naoAvaliados()).isEqualTo(10);
        assertThat(com.avaliados()).isEqualTo(8);
        assertThat(com.total()).isEqualTo(18);
    }

    @Test
    void naoAvaliadoDeveAparecerNaCoberturaEEmNenhumOutroLugar() {
        Montagem montagem = new Montagem();
        montagem.caso(1, RotuloEsperado.ACHADO, ACHADO);
        montagem.caso(2, RotuloEsperado.CONFORME, CONFORME);
        montagem.caso(3, RotuloEsperado.ACHADO, NAO_AVALIADO);
        montagem.caso(4, RotuloEsperado.CONFORME, NAO_AVALIADO);

        ContagemDeAcuracia contagem = montagem.medir().daRegra(REGRA_PRIMEIRA).orElseThrow().contagem();

        assertThat(contagem.naoAvaliados()).isEqualTo(2);
        assertThat(contagem.avaliados()).isEqualTo(2);
        assertThat(contagem.cobertura().valor()).contains(new BigDecimal("0.5000"));
        assertThat(contagem.precisao().valor()).contains(new BigDecimal("1.0000"));
        assertThat(contagem.recall().valor()).contains(new BigDecimal("1.0000"));
    }

    @Test
    void motorQueNadaAvaliouDeveTerMetricasIndefinidasENaoPerfeitas() {
        Montagem montagem = new Montagem();
        for (int item = 1; item <= 20; item++) {
            montagem.caso(item, RotuloEsperado.ACHADO, NAO_AVALIADO);
        }

        ContagemDeAcuracia contagem = montagem.medir().daRegra(REGRA_PRIMEIRA).orElseThrow().contagem();

        assertThat(contagem.precisao().estaDefinida()).isFalse();
        assertThat(contagem.recall().estaDefinida()).isFalse();
        assertThat(contagem.f1().estaDefinida()).isFalse();
        assertThat(contagem.cobertura().valor()).contains(new BigDecimal("0.0000"));
        assertThat(contagem.naoAvaliados()).isEqualTo(20);
    }

    @Test
    void linhaDeGabaritoSemAvaliacaoCorrespondenteDeveSerReportadaSeparadamente() {
        List<LinhaDeGabarito> linhas = List.of(
                linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO),
                linha(3, OUTRA_CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO));
        List<Avaliacao> avaliacoes = List.of(avaliacao(CHAVE, 1, REGRA_PRIMEIRA, ACHADO));

        RelatorioDeAcuracia relatorio = comparar(new Gabarito(linhas), avaliacoes);
        ContagemDeAcuracia contagem = relatorio.daRegra(REGRA_PRIMEIRA).orElseThrow().contagem();

        assertThat(contagem.semAvaliacao()).isEqualTo(1);
        assertThat(contagem.avaliados()).isEqualTo(1);
        assertThat(contagem.precisao().valor()).contains(new BigDecimal("1.0000"));
        assertThat(contagem.cobertura().valor()).contains(new BigDecimal("0.5000"));
        assertThat(relatorio.gabaritoSemAvaliacao())
                .containsExactly(endereco(OUTRA_CHAVE, 1, REGRA_PRIMEIRA));
    }

    @Test
    void avaliacaoSemLinhaNoGabaritoDeveFicarForaDaMedicaoESerContada() {
        List<LinhaDeGabarito> linhas = List.of(linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO));
        List<Avaliacao> avaliacoes = List.of(
                avaliacao(CHAVE, 1, REGRA_PRIMEIRA, ACHADO),
                avaliacao(CHAVE, 2, REGRA_PRIMEIRA, ACHADO),
                avaliacao(CHAVE, 3, REGRA_PRIMEIRA, CONFORME));

        RelatorioDeAcuracia relatorio = comparar(new Gabarito(linhas), avaliacoes);

        assertThat(relatorio.avaliacoesSemLinhaNoGabarito()).isEqualTo(2);
        assertThat(relatorio.consolidado().total()).isEqualTo(1);
        assertThat(relatorio.avaliacoesProduzidas()).isEqualTo(3);
    }

    @Test
    void avaliacaoDeDocumentoInteiroNaoTemEnderecoEFicaForaDaMedicao() {
        List<LinhaDeGabarito> linhas = List.of(linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO));
        List<Avaliacao> avaliacoes = List.of(
                avaliacao(CHAVE, 1, REGRA_PRIMEIRA, ACHADO),
                avaliacaoDeDocumento(CHAVE, REGRA_PRIMEIRA));

        RelatorioDeAcuracia relatorio = comparar(new Gabarito(linhas), avaliacoes);

        assertThat(relatorio.avaliacoesSemLinhaNoGabarito()).isEqualTo(1);
        assertThat(relatorio.consolidado()).isEqualTo(new ContagemDeAcuracia(1, 0, 0, 0, 0, 0));
    }

    @Test
    void deveDarLinhaZeradaATodaRegraDoConjuntoQueOGabaritoNaoCita() {
        List<LinhaDeGabarito> linhas = List.of(linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO));
        List<Avaliacao> avaliacoes = List.of(avaliacao(CHAVE, 1, REGRA_PRIMEIRA, ACHADO));

        RelatorioDeAcuracia relatorio = comparar(new Gabarito(linhas), avaliacoes);
        ContagemDeAcuracia naoMedida = relatorio.daRegra(REGRA_SEGUNDA).orElseThrow().contagem();

        assertThat(relatorio.porRegra()).hasSize(2);
        assertThat(naoMedida).isEqualTo(ContagemDeAcuracia.nenhuma());
        assertThat(naoMedida.precisao().estaDefinida()).isFalse();
        assertThat(naoMedida.cobertura().estaDefinida()).isFalse();
    }

    /**
     * O consolidado soma células; a média das precisões por regra daria outro
     * número, e o teste escolhe contagens em que os dois divergem.
     *
     * <p>{@code RX1} tem VP=1 e FP=1, precisão 0,5. {@code RX2} tem VP=6 e FP=0,
     * precisão 1,0. A média das duas seria 0,75. A soma de células dá 7/8 =
     * 0,875, que é o que o relatório reporta.</p>
     */
    @Test
    void consolidadoDeveSomarCelulasENaoMediarAsMetricasPorRegra() {
        List<LinhaDeGabarito> linhas = new ArrayList<>();
        List<Avaliacao> avaliacoes = new ArrayList<>();

        linhas.add(linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO));
        avaliacoes.add(avaliacao(CHAVE, 1, REGRA_PRIMEIRA, ACHADO));
        linhas.add(linha(3, CHAVE, 2, REGRA_PRIMEIRA, RotuloEsperado.CONFORME));
        avaliacoes.add(avaliacao(CHAVE, 2, REGRA_PRIMEIRA, ACHADO));

        for (int item = 1; item <= 6; item++) {
            linhas.add(linha(3 + item, CHAVE, item, REGRA_SEGUNDA, RotuloEsperado.ACHADO));
            avaliacoes.add(avaliacao(CHAVE, item, REGRA_SEGUNDA, ACHADO));
        }

        RelatorioDeAcuracia relatorio = comparar(new Gabarito(linhas), avaliacoes);

        assertThat(relatorio.daRegra(REGRA_PRIMEIRA).orElseThrow().contagem().precisao().valor())
                .contains(new BigDecimal("0.5000"));
        assertThat(relatorio.daRegra(REGRA_SEGUNDA).orElseThrow().contagem().precisao().valor())
                .contains(new BigDecimal("1.0000"));

        assertThat(relatorio.consolidado()).isEqualTo(new ContagemDeAcuracia(7, 1, 0, 0, 0, 0));
        assertThat(relatorio.consolidado().precisao().valor()).contains(new BigDecimal("0.8750"));
    }

    @Test
    void deveRecusarGabaritoQueCitaRegraForaDoConjunto() {
        List<LinhaDeGabarito> linhas = List.of(linha(2, CHAVE, 1, "RX9", RotuloEsperado.ACHADO));

        assertThatThrownBy(() -> comparar(new Gabarito(linhas), List.of()))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("RX9")
                .hasMessageContaining(REGRA_PRIMEIRA);
    }

    @Test
    void deveRecusarComparacaoSemGabarito() {
        assertThatThrownBy(() -> comparar(null, List.of()))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("gabarito");
    }

    /** VP=3, FP=1, FN=2, VN=2 na regra {@code RX1}. */
    private Montagem cenarioBase() {
        Montagem montagem = new Montagem();
        montagem.caso(1, RotuloEsperado.ACHADO, ACHADO);
        montagem.caso(2, RotuloEsperado.ACHADO, ACHADO);
        montagem.caso(3, RotuloEsperado.ACHADO, ACHADO);
        montagem.caso(4, RotuloEsperado.CONFORME, ACHADO);
        montagem.caso(5, RotuloEsperado.ACHADO, CONFORME);
        montagem.caso(6, RotuloEsperado.ACHADO, CONFORME);
        montagem.caso(7, RotuloEsperado.CONFORME, CONFORME);
        montagem.caso(8, RotuloEsperado.CONFORME, CONFORME);
        return montagem;
    }

    private RelatorioDeAcuracia comparar(Gabarito gabarito, List<Avaliacao> avaliacoes) {
        return comparador.comparar(
                gabarito, avaliacoes, CONJUNTO, VERSAO_CATALOGO, VERSAO_CONJUNTO, 1, 1);
    }

    /** Monta pares de linha de gabarito e avaliação do motor, um item por caso. */
    private final class Montagem {

        private final List<LinhaDeGabarito> linhas = new ArrayList<>();
        private final List<Avaliacao> avaliacoes = new ArrayList<>();

        void caso(int numeroItem, RotuloEsperado esperado, ResultadoAvaliacao obtido) {
            linhas.add(linha(numeroItem + 1, CHAVE, numeroItem, REGRA_PRIMEIRA, esperado));
            avaliacoes.add(avaliacao(CHAVE, numeroItem, REGRA_PRIMEIRA, obtido));
        }

        RelatorioDeAcuracia medir() {
            return comparar(new Gabarito(linhas), avaliacoes);
        }
    }
}
