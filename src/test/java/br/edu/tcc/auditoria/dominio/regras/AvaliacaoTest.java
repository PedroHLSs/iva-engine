package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.excecao.AvaliacaoInvalida;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O desfecho de uma regra, e o que cada desfecho é obrigado a carregar. */
class AvaliacaoTest {

    private static final String REGRA_ID = "R00";
    private static final String REGRA_VERSAO = "0.0.0-ficticia";

    @Test
    void naoAvaliadaDeveExigirMotivo() {
        assertThatThrownBy(() -> Avaliacao.naoAvaliada(REGRA_ID, REGRA_VERSAO, chave(), OptionalInt.of(1), "  "))
                .isInstanceOf(AvaliacaoInvalida.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void conformeNaoDeveTerMotivoNemAchado() {
        Avaliacao conforme = Avaliacao.conforme(REGRA_ID, REGRA_VERSAO, chave(), OptionalInt.of(1));

        assertThat(conforme.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
        assertThat(conforme.achado()).isEmpty();
        assertThat(conforme.motivoDaNaoAvaliacao()).isEmpty();
    }

    @Test
    void naoAvaliadaEConformeNaoPodemSeConfundir() {
        // Se as duas se parecessem na saída, o relatório passaria falta de dado
        // por conformidade — que é a falha que este projeto mais teme.
        Avaliacao conforme = Avaliacao.conforme(REGRA_ID, REGRA_VERSAO, chave(), OptionalInt.of(1));
        Avaliacao naoAvaliada =
                Avaliacao.naoAvaliada(REGRA_ID, REGRA_VERSAO, chave(), OptionalInt.of(1), "faltou tabela");

        assertThat(conforme).isNotEqualTo(naoAvaliada);
        assertThat(conforme.resultado()).isNotEqualTo(naoAvaliada.resultado());
        assertThat(naoAvaliada.motivoDaNaoAvaliacao()).contains("faltou tabela");
    }

    @Test
    void comAchadoDeveHerdarAIdentificacaoDoProprioAchado() {
        Avaliacao avaliacao = Avaliacao.comAchado(achado());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.regraId()).isEqualTo(REGRA_ID);
        assertThat(avaliacao.regraVersao()).isEqualTo(REGRA_VERSAO);
        assertThat(avaliacao.numeroItem()).hasValue(1);
        assertThat(avaliacao.motivoDaNaoAvaliacao()).isEmpty();
    }

    @Test
    void comAchadoDeveExigirOAchado() {
        assertThatThrownBy(() -> Avaliacao.comAchado(null))
                .isInstanceOf(AvaliacaoInvalida.class);
    }

    @Test
    void deveExigirRegraEDocumentoEmTodaAvaliacao() {
        assertThatThrownBy(() -> Avaliacao.conforme("  ", REGRA_VERSAO, chave(), OptionalInt.of(1)))
                .isInstanceOf(AvaliacaoInvalida.class);
        assertThatThrownBy(() -> Avaliacao.conforme(REGRA_ID, REGRA_VERSAO, null, OptionalInt.of(1)))
                .isInstanceOf(AvaliacaoInvalida.class);
        assertThatThrownBy(() -> Avaliacao.conforme(REGRA_ID, REGRA_VERSAO, chave(), null))
                .isInstanceOf(AvaliacaoInvalida.class);
    }

    @Test
    void deveRecusarNumeroDeItemMenorQueUm() {
        assertThatThrownBy(() -> Avaliacao.conforme(REGRA_ID, REGRA_VERSAO, chave(), OptionalInt.of(0)))
                .isInstanceOf(AvaliacaoInvalida.class);
    }

    private static ChaveAcesso chave() {
        return new ChaveAcesso(CenarioFicticio.CHAVE_PRIMEIRA);
    }

    private static Achado achado() {
        return new Achado(
                REGRA_ID,
                REGRA_VERSAO,
                Severidade.GRAVE,
                chave(),
                OptionalInt.of(1),
                List.of(new Evidencia(
                        "campoFicticio",
                        Optional.of("99,99"),
                        Optional.empty(),
                        new OrigemEvidencia.DaRegra("derivação fictícia para teste"))),
                CatalogoFicticio.DISPOSITIVO,
                PeriodoVigencia.de(CatalogoFicticio.INICIO, CatalogoFicticio.FIM),
                ValorEmRisco.naoCalculavel("cenário fictício de teste"));
    }
}
