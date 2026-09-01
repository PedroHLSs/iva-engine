package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TratativaTest {

    private static final ChaveAcesso CHAVE_ACESSO = new ChaveAcesso(CenarioFicticio.CHAVE_PRIMEIRA);
    private static final String REGRA = "REGRA-FICTICIA-000";
    private static final String VERSAO = "0.0.0-ficticia";
    private static final String VERSAO_SEGUINTE = "0.0.1-ficticia";
    private static final Instant AGORA = Instant.parse("1900-01-01T00:00:00Z");

    private static final HashDoItem HASH =
            HashDoItem.de(CHAVE_ACESSO, ConstrutorDeItem.item().numero(1).construir());

    @Test
    void deveExigirJustificativa() {
        assertThatThrownBy(() -> tratativa(DecisaoDeTratativa.ACEITO, "   "))
                .as("apontamento tratado sem razão registrada é apontamento apagado")
                .isInstanceOf(TratativaInvalida.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    void deveExigirDecisao() {
        assertThatThrownBy(() -> tratativa(null, "justificativa fictícia de teste"))
                .isInstanceOf(TratativaInvalida.class);
    }

    @Test
    void deveExigirMomentoDeRegistro() {
        assertThatThrownBy(() -> new Tratativa(
                new ChaveDeTratativa(HASH, REGRA, VERSAO),
                DecisaoDeTratativa.REFUTADO,
                "justificativa fictícia de teste",
                null))
                .isInstanceOf(TratativaInvalida.class);
    }

    @Test
    void deveSeAplicarAoApontamentoDaMesmaRegraNaMesmaVersao() {
        Tratativa tratativa = tratativa(DecisaoDeTratativa.REFUTADO, "justificativa fictícia de teste");

        assertThat(tratativa.seAplicaA(achado(REGRA, VERSAO), HASH)).isTrue();
    }

    @Test
    void naoDeveSeAplicarQuandoAVersaoDaRegraMuda() {
        Tratativa tratativa = tratativa(DecisaoDeTratativa.REFUTADO, "justificativa fictícia de teste");

        assertThat(tratativa.seAplicaA(achado(REGRA, VERSAO_SEGUINTE), HASH))
                .as("a justificativa respondeu a um critério; critério novo é pergunta nova, "
                        + "e o apontamento reabre")
                .isFalse();
    }

    @Test
    void naoDeveSeAplicarQuandoOConteudoDoItemMuda() {
        Tratativa tratativa = tratativa(DecisaoDeTratativa.ACEITO, "justificativa fictícia de teste");
        HashDoItem outroItem = HashDoItem.de(
                CHAVE_ACESSO, ConstrutorDeItem.item().numero(1).baseCalculoIbs("11.11").construir());

        assertThat(tratativa.seAplicaA(achado(REGRA, VERSAO), outroItem)).isFalse();
    }

    @Test
    void deveExigirResumoDoItemNaChave() {
        assertThatThrownBy(() -> new ChaveDeTratativa(null, REGRA, VERSAO))
                .isInstanceOf(TratativaInvalida.class);
    }

    @Test
    void deveDerivarAChaveDoApontamento() {
        ChaveDeTratativa chave = ChaveDeTratativa.de(HASH, achado(REGRA, VERSAO));

        assertThat(chave).isEqualTo(new ChaveDeTratativa(HASH, REGRA, VERSAO));
    }

    private static Tratativa tratativa(DecisaoDeTratativa decisao, String justificativa) {
        return new Tratativa(
                new ChaveDeTratativa(HASH, REGRA, VERSAO), decisao, justificativa, AGORA);
    }

    private static Achado achado(String regraId, String regraVersao) {
        return new Achado(
                regraId,
                regraVersao,
                Severidade.MODERADA,
                CHAVE_ACESSO,
                OptionalInt.of(1),
                List.of(new Evidencia(
                        "campoFicticio",
                        Optional.of("99,99"),
                        Optional.empty(),
                        new OrigemEvidencia.DaRegra("derivação fictícia para teste"))),
                "FUNDAMENTO FICTICIO PARA TESTE",
                PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1)),
                ValorEmRisco.naoCalculavel("motivo fictício de teste"));
    }
}
