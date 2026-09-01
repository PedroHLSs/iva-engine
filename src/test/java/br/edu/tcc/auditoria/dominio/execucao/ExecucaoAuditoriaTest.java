package br.edu.tcc.auditoria.dominio.execucao;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.excecao.ExecucaoInvalida;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecucaoAuditoriaTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant AGORA = Instant.parse("1900-01-01T00:00:00Z");
    private static final String HASH_DE_ENTRADA = "0".repeat(64);
    private static final String CHAVE = "1".repeat(44);
    private static final List<String> REGRAS = List.of("RXX", "RYY", "RZZ");

    @Test
    void deveContarApontamentosPorSeveridadeEPorRegra() {
        ExecucaoAuditoria execucao = execucaoCom(List.of(
                achado("RXX", Severidade.CRITICA),
                achado("RXX", Severidade.GRAVE),
                achado("RYY", Severidade.GRAVE)));

        assertThat(execucao.achadosDe(Severidade.CRITICA)).isEqualTo(1);
        assertThat(execucao.achadosDe(Severidade.GRAVE)).isEqualTo(2);
        assertThat(execucao.achadosDaRegra("RXX")).isEqualTo(2);
        assertThat(execucao.achadosDaRegra("RYY")).isEqualTo(1);
        assertThat(execucao.quantidadeDeAchados()).isEqualTo(3);
    }

    @Test
    void deveContarZeroNasSeveridadesSemApontamento() {
        ExecucaoAuditoria execucao = execucaoCom(List.of(achado("RXX", Severidade.CRITICA)));

        assertThat(execucao.achadosPorSeveridade())
                .as("severidade omitida obrigaria o leitor a adivinhar se não houve apontamento "
                        + "ou se ninguém olhou")
                .containsKeys(Severidade.values())
                .containsEntry(Severidade.INFORMATIVA, 0);
    }

    @Test
    void deveContarZeroNasRegrasQueNadaApontaram() {
        ExecucaoAuditoria execucao = execucaoCom(List.of(achado("RXX", Severidade.CRITICA)));

        assertThat(execucao.achadosPorRegra())
                .as("regra que rodou e nada encontrou não pode sumir do relatório")
                .containsEntry("RYY", 0)
                .containsEntry("RZZ", 0);
    }

    @Test
    void deveContarZeroEmTudoQuandoNaoHouveApontamento() {
        ExecucaoAuditoria execucao = execucaoCom(List.of());

        assertThat(execucao.quantidadeDeAchados()).isZero();
        assertThat(execucao.achadosPorRegra()).hasSize(REGRAS.size());
        assertThat(execucao.achadosPorSeveridade()).hasSize(Severidade.values().length);
    }

    @Test
    void deveExigirOQueTornaORelatorioReproduzivel() {
        assertThatThrownBy(() -> new ExecucaoAuditoria(
                ID, AGORA, "  ", "catalogo-ficticio", "0.0-ficticia", 0, 0, Map.of(), Map.of()))
                .isInstanceOf(ExecucaoInvalida.class)
                .hasMessageContaining("hashEntrada");

        assertThatThrownBy(() -> new ExecucaoAuditoria(
                ID, AGORA, HASH_DE_ENTRADA, "  ", "0.0-ficticia", 0, 0, Map.of(), Map.of()))
                .isInstanceOf(ExecucaoInvalida.class)
                .hasMessageContaining("versaoCatalogo");

        assertThatThrownBy(() -> new ExecucaoAuditoria(
                ID, AGORA, HASH_DE_ENTRADA, "catalogo-ficticio", "  ", 0, 0, Map.of(), Map.of()))
                .isInstanceOf(ExecucaoInvalida.class)
                .hasMessageContaining("versaoConjuntoRegras");
    }

    @Test
    void deveRecusarContagemNegativa() {
        assertThatThrownBy(() -> new ExecucaoAuditoria(
                ID, AGORA, HASH_DE_ENTRADA, "catalogo-ficticio", "0.0-ficticia", 0, 0,
                Map.of(Severidade.GRAVE, -1), Map.of()))
                .isInstanceOf(ExecucaoInvalida.class);
    }

    @Test
    void deveRecusarQuantidadeNegativaDeDocumentos() {
        assertThatThrownBy(() -> new ExecucaoAuditoria(
                ID, AGORA, HASH_DE_ENTRADA, "catalogo-ficticio", "0.0-ficticia", -1, 0,
                Map.of(), Map.of()))
                .isInstanceOf(ExecucaoInvalida.class)
                .hasMessageContaining("quantidadeDocumentos");
    }

    @Test
    void deveEntregarContagensImutaveis() {
        ExecucaoAuditoria execucao = execucaoCom(List.of());

        assertThatThrownBy(() -> execucao.achadosPorRegra().put("RXX", 99))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ExecucaoAuditoria execucaoCom(List<Achado> achados) {
        return ExecucaoAuditoria.de(
                ID, AGORA, HASH_DE_ENTRADA, "catalogo-ficticio", "0.0-ficticia",
                1, achados.size(), REGRAS, achados);
    }

    private static Achado achado(String regraId, Severidade severidade) {
        return new Achado(
                regraId,
                "0.0.0-ficticia",
                severidade,
                new ChaveAcesso(CHAVE),
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
