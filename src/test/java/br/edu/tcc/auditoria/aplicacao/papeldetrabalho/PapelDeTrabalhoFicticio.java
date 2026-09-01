package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Um papel de trabalho conhecido, para conferir célula a célula o que a
 * exportação escreve.
 *
 * <p>Tudo aqui é explicitamente fictício: regras {@code RXX}, {@code RYY} e
 * {@code RZZ}, fundamento em maiúsculas dizendo que é fictício, vigência em 1900
 * e pseudônimos de um caractere repetido. Nada afirma coisa alguma sobre a
 * legislação, e nenhum valor pode ser confundido com dado real.</p>
 */
public final class PapelDeTrabalhoFicticio {

    public static final UUID EXECUCAO =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    public static final Instant DATA_HORA = Instant.parse("1900-01-02T03:04:05Z");
    public static final String HASH_DE_ENTRADA = "a".repeat(64);
    public static final String VERSAO_CATALOGO = "catalogo-ficticio-0.0";
    public static final String VERSAO_REGRAS = "0.0-ficticia";
    public static final int DOCUMENTOS = 7;
    public static final int ITENS = 19;

    public static final String PSEUDONIMO_PRIMEIRO = "b".repeat(64);
    public static final String PSEUDONIMO_SEGUNDO = "c".repeat(64);

    public static final String REGRA_CRITICA = "RXX";
    public static final String REGRA_GRAVE = "RYY";
    public static final String REGRA_SEM_ACHADO = "RZZ";
    public static final String VERSAO_DA_REGRA = "0.0.0-ficticia";

    public static final String FUNDAMENTO = "FUNDAMENTO FICTICIO PARA TESTE";
    public static final LocalDate VIGENCIA_INICIO = LocalDate.of(1900, 1, 1);
    public static final LocalDate VIGENCIA_FIM = LocalDate.of(1900, 12, 31);
    public static final LocalDate DATA_EMISSAO = LocalDate.of(1900, 6, 15);
    public static final BigDecimal VALOR_EM_RISCO = new BigDecimal("99.99");
    public static final String MOTIVO_DO_VALOR_AUSENTE = "Motivo ficticio de valor nao aferivel.";

    public static final String JUSTIFICATIVA = "Justificativa ficticia de teste.";
    public static final Instant TRATADO_EM = Instant.parse("1900-02-03T04:05:06Z");

    public static final String MOTIVO_REPETIDO = "Motivo ficticio que se repete no lote.";
    public static final String MOTIVO_UNICO = "Motivo ficticio que aparece uma vez so.";

    private PapelDeTrabalhoFicticio() {
    }

    /** Papel de trabalho com dois achados, três não avaliados e dois motivos agrupados. */
    public static PapelDeTrabalho completo() {
        return new PapelDeTrabalho(execucao(), achados(), naoAvaliados(), motivosAgrupados(), 2);
    }

    /** Papel de trabalho de uma rodada que não encontrou nada e avaliou tudo. */
    public static PapelDeTrabalho semNada() {
        return new PapelDeTrabalho(execucaoSemAchados(), List.of(), List.of(), List.of(), 0);
    }

    public static ExecucaoAuditoria execucao() {
        Map<Severidade, Integer> porSeveridade = new LinkedHashMap<>();
        porSeveridade.put(Severidade.CRITICA, 1);
        porSeveridade.put(Severidade.GRAVE, 1);
        porSeveridade.put(Severidade.MODERADA, 0);
        porSeveridade.put(Severidade.INFORMATIVA, 0);

        Map<String, Integer> porRegra = new LinkedHashMap<>();
        porRegra.put(REGRA_CRITICA, 1);
        porRegra.put(REGRA_GRAVE, 1);
        porRegra.put(REGRA_SEM_ACHADO, 0);

        return new ExecucaoAuditoria(
                EXECUCAO, DATA_HORA, HASH_DE_ENTRADA, VERSAO_CATALOGO, VERSAO_REGRAS,
                DOCUMENTOS, ITENS, porSeveridade, porRegra);
    }

    private static ExecucaoAuditoria execucaoSemAchados() {
        return ExecucaoAuditoria.de(
                EXECUCAO, DATA_HORA, HASH_DE_ENTRADA, VERSAO_CATALOGO, VERSAO_REGRAS,
                DOCUMENTOS, ITENS, List.of(REGRA_CRITICA, REGRA_GRAVE, REGRA_SEM_ACHADO), List.of());
    }

    /**
     * Dois achados.
     *
     * <p>O primeiro tem duas evidências, valor em risco aferido e tratativa
     * registrada. O segundo tem uma evidência, valor não aferível e continua em
     * aberto — de modo que a planilha precise mostrar os dois lados de cada
     * coluna opcional.</p>
     */
    public static List<LinhaDeAchado> achados() {
        return List.of(
                new LinhaDeAchado(
                        PSEUDONIMO_PRIMEIRO, "99", "999", "111111", DATA_EMISSAO, Uf.SP,
                        1, REGRA_CRITICA, VERSAO_DA_REGRA, Severidade.CRITICA,
                        List.of("cClassTrib", "cstIbs"),
                        List.of(Optional.of("999999"), Optional.empty()),
                        List.of(Optional.empty(), Optional.of("AAA")),
                        FUNDAMENTO, VIGENCIA_INICIO, Optional.of(VIGENCIA_FIM),
                        Optional.of(VALOR_EM_RISCO), Optional.empty(),
                        StatusDeTratativa.ACEITO, Optional.of(JUSTIFICATIVA),
                        Optional.of(TRATADO_EM)),
                new LinhaDeAchado(
                        PSEUDONIMO_SEGUNDO, "99", "999", "222222", DATA_EMISSAO, Uf.MG,
                        2, REGRA_GRAVE, VERSAO_DA_REGRA, Severidade.GRAVE,
                        List.of("valorIbsUf"),
                        List.of(Optional.of("8.88")),
                        List.of(Optional.of("9.99")),
                        FUNDAMENTO, VIGENCIA_INICIO, Optional.empty(),
                        Optional.empty(), Optional.of(MOTIVO_DO_VALOR_AUSENTE),
                        StatusDeTratativa.ABERTO, Optional.empty(), Optional.empty()));
    }

    public static List<LinhaNaoAvaliada> naoAvaliados() {
        return List.of(
                new LinhaNaoAvaliada(PSEUDONIMO_PRIMEIRO, "99", "999", "111111", 1,
                        REGRA_SEM_ACHADO, VERSAO_DA_REGRA, MOTIVO_REPETIDO),
                new LinhaNaoAvaliada(PSEUDONIMO_SEGUNDO, "99", "999", "222222", 2,
                        REGRA_SEM_ACHADO, VERSAO_DA_REGRA, MOTIVO_REPETIDO),
                new LinhaNaoAvaliada(PSEUDONIMO_SEGUNDO, "99", "999", "222222", 2,
                        REGRA_GRAVE, VERSAO_DA_REGRA, MOTIVO_UNICO));
    }

    public static List<MotivoAgrupado> motivosAgrupados() {
        return List.of(
                new MotivoAgrupado(REGRA_SEM_ACHADO, MOTIVO_REPETIDO, 2),
                new MotivoAgrupado(REGRA_GRAVE, MOTIVO_UNICO, 1));
    }
}
