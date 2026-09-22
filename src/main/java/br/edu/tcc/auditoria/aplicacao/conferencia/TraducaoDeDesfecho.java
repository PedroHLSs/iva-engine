package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.Optional;

/**
 * A tabela que traduz o que o motor produz no que a interface diz.
 *
 * <p>É o único lugar do sistema em que essa tradução acontece. Se ela estiver
 * errada, a interface mente — e mente com aparência de conclusão firme, que é o
 * pior defeito possível numa ferramenta de auditoria.</p>
 *
 * <table>
 *   <caption>A tradução, inteira</caption>
 *   <tr><th>Resultado</th><th>Severidade</th><th>Estado</th></tr>
 *   <tr><td>CONFORME</td><td>(não há)</td><td>SEM_DIVERGENCIA_IDENTIFICADA</td></tr>
 *   <tr><td>NAO_AVALIADO</td><td>(não há)</td><td>NAO_FOI_POSSIVEL_CONCLUIR</td></tr>
 *   <tr><td>ACHADO</td><td>CRITICA</td><td>POSSIVEL_DIVERGENCIA</td></tr>
 *   <tr><td>ACHADO</td><td>GRAVE</td><td>POSSIVEL_DIVERGENCIA</td></tr>
 *   <tr><td>ACHADO</td><td>MODERADA</td><td>POSSIVEL_DIVERGENCIA</td></tr>
 *   <tr><td>ACHADO</td><td>INFORMATIVA</td><td>REQUER_CONFERENCIA</td></tr>
 * </table>
 *
 * <h2>A chave é a severidade, não o identificador da regra</h2>
 *
 * <p>Não há nenhum {@code "R04"} escrito aqui, e não deve haver. Uma tabela por
 * identificador precisaria ser editada a cada regra nova, e quem esquecesse de
 * editá-la descobriria pelo relatório. Pela severidade, regra nova nasce
 * traduzida — e o {@code switch} sobre {@link Severidade} é exaustivo, sem
 * {@code default}, de modo que <strong>acrescentar uma severidade quebra a
 * compilação</strong> em vez de cair num ramo escolhido por quem escreveu este
 * arquivo anos antes.</p>
 *
 * <h2>Por que informativa não é divergência</h2>
 *
 * <p>A única regra informativa do conjunto é o R04, e ela mesma diz por quê:
 * "o sistema aponta, não aconselha: pode haver razão legítima para o
 * contribuinte não ter aplicado o tratamento, e afirmar erro aqui seria
 * ultrapassar o que a ferramenta se propõe a fazer. O apontamento diz que a
 * situação merece leitura humana". "Merece leitura humana" é a definição de
 * {@link EstadoDeConferencia#REQUER_CONFERENCIA}.</p>
 *
 * <h2>Combinação impossível é recusada, não acomodada</h2>
 *
 * <p>{@code ACHADO} sem severidade e {@code CONFORME} com severidade não
 * existem no modelo, e aqui não ganham um estado por conveniência: ganham
 * {@link ConferenciaInvalida}. É o que torna as doze combinações testáveis, e
 * não só as seis alcançáveis.</p>
 */
public final class TraducaoDeDesfecho {

    private TraducaoDeDesfecho() {
    }

    /**
     * A tradução, na forma em que ela é especificação.
     *
     * @param resultado  o desfecho que o motor produziu
     * @param severidade a severidade do apontamento; vazia quando não houve
     *                   apontamento, e obrigatória quando houve
     */
    public static EstadoDeConferencia de(
            ResultadoAvaliacao resultado, Optional<Severidade> severidade) {

        if (resultado == null) {
            throw new ConferenciaInvalida(
                    "Não há desfecho a traduzir. Sem resultado do motor não há estado de interface, e "
                            + "escolher um seria a camada de apresentação inventando auditoria.");
        }
        if (severidade == null) {
            throw new ConferenciaInvalida(
                    "Desfecho sem apontamento se representa com Optional.empty() na severidade, nunca "
                            + "com nulo.");
        }

        return switch (resultado) {
            case ACHADO -> deApontamento(exigirSeveridade(severidade));
            case CONFORME -> {
                exigirSemSeveridade(severidade, ResultadoAvaliacao.CONFORME);
                yield EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
            }
            case NAO_AVALIADO -> {
                exigirSemSeveridade(severidade, ResultadoAvaliacao.NAO_AVALIADO);
                yield EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR;
            }
        };
    }

    /**
     * A tradução, na forma em que ela é usada.
     *
     * <p>Toma a severidade do próprio apontamento, de modo que a exigência de
     * "severidade presente se e somente se houve achado" fica satisfeita por
     * construção: {@code Avaliacao.Conforme} e {@code Avaliacao.NaoAvaliada} não
     * têm achado de onde tirá-la.</p>
     */
    public static EstadoDeConferencia de(Avaliacao avaliacao) {
        if (avaliacao == null) {
            throw new ConferenciaInvalida("Não há avaliação a traduzir.");
        }
        return de(avaliacao.resultado(), avaliacao.achado().map(Achado::severidade));
    }

    /**
     * Exaustivo de propósito, e sem {@code default}.
     *
     * <p>Uma severidade nova no domínio faz este método parar de compilar, e a
     * decisão sobre de que lado ela fica é tomada ali, por quem a criou.</p>
     */
    private static EstadoDeConferencia deApontamento(Severidade severidade) {
        return switch (severidade) {
            case CRITICA, GRAVE, MODERADA -> EstadoDeConferencia.POSSIVEL_DIVERGENCIA;
            case INFORMATIVA -> EstadoDeConferencia.REQUER_CONFERENCIA;
        };
    }

    private static Severidade exigirSeveridade(Optional<Severidade> severidade) {
        return severidade.orElseThrow(() -> new ConferenciaInvalida(
                "Um apontamento sem severidade não é traduzível: é ela que separa a divergência da "
                        + "situação que só pede leitura humana. Todo Achado carrega a sua."));
    }

    private static void exigirSemSeveridade(
            Optional<Severidade> severidade, ResultadoAvaliacao resultado) {

        severidade.ifPresent(grau -> {
            throw new ConferenciaInvalida(
                    ("O desfecho %s veio com severidade %s. Severidade é atributo de apontamento, e "
                            + "aceitar uma aqui deixaria a tradução depender de um campo que o motor "
                            + "não produziu.").formatted(resultado, grau));
        });
    }
}
