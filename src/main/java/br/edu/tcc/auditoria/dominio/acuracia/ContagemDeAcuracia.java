package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Quantas vezes cada {@link Desfecho} ocorreu, e o que se calcula a partir disso.
 *
 * <p>É a matriz de confusão de uma regra — ou do conjunto inteiro, quando as
 * contagens são somadas —, acrescida das duas colunas que uma matriz de confusão
 * não tem e que este sistema precisa ter: {@link #naoAvaliados()} e
 * {@link #semAvaliacao()}.</p>
 *
 * <h2>O que entra em precisão e recall, e o que não entra</h2>
 *
 * <p>Precisão e recall se calculam <strong>somente</strong> sobre
 * {@link #avaliados()}, que é a soma dos quatro quadrantes. As duas outras
 * contagens não aparecem em nenhum denominador dessas fórmulas, e é isso que
 * garante que um lote em que o motor não conseguiu julgar nada saia com precisão
 * indefinida, e não com precisão perfeita.</p>
 *
 * <p>Elas aparecem em {@link #cobertura()}, que é a métrica própria delas:
 * avaliados sobre o total do gabarito. Cobertura baixa com precisão alta é um
 * resultado legítimo e comum — o sistema acerta o que julga e julga pouco —, e
 * só é legível porque os dois números são reportados lado a lado.</p>
 *
 * <h2>O F1 e o caso em que ele não existe</h2>
 *
 * <p>F1 é calculado como {@code 2VP / (2VP + FP + FN)}, que é algebricamente
 * igual à média harmônica de precisão e recall onde as duas são definidas, e não
 * passa pela divisão intermediária. Ainda assim ele <strong>só é definido quando
 * precisão e recall também são</strong>: dizer "F1 = 0" sobre uma regra cuja
 * precisão é indefinida seria afirmar um resumo de um número que não existe.</p>
 *
 * @param verdadeirosPositivos gabarito ACHADO, motor apontou
 * @param falsosPositivos      gabarito CONFORME, motor apontou
 * @param falsosNegativos      gabarito ACHADO, motor concluiu conforme
 * @param verdadeirosNegativos gabarito CONFORME, motor concluiu conforme
 * @param naoAvaliados         o motor não pôde julgar; fora de precisão e recall
 * @param semAvaliacao         o gabarito aponta item que o motor não viu
 */
public record ContagemDeAcuracia(
        int verdadeirosPositivos,
        int falsosPositivos,
        int falsosNegativos,
        int verdadeirosNegativos,
        int naoAvaliados,
        int semAvaliacao) {

    public ContagemDeAcuracia {
        exigirNaoNegativo(verdadeirosPositivos, "verdadeirosPositivos");
        exigirNaoNegativo(falsosPositivos, "falsosPositivos");
        exigirNaoNegativo(falsosNegativos, "falsosNegativos");
        exigirNaoNegativo(verdadeirosNegativos, "verdadeirosNegativos");
        exigirNaoNegativo(naoAvaliados, "naoAvaliados");
        exigirNaoNegativo(semAvaliacao, "semAvaliacao");
    }

    /** Contagem zerada, ponto de partida das somas. */
    public static ContagemDeAcuracia nenhuma() {
        return new ContagemDeAcuracia(0, 0, 0, 0, 0, 0);
    }

    /** Conta os desfechos de um confronto inteiro. */
    public static ContagemDeAcuracia contar(Collection<Desfecho> desfechos) {
        if (desfechos == null) {
            throw new AcuraciaInvalida(
                    "A coleção de desfechos deve ser vazia quando nada foi confrontado, nunca nula.");
        }
        if (desfechos.stream().anyMatch(Objects::isNull)) {
            throw new AcuraciaInvalida("A coleção de desfechos não pode conter elemento nulo.");
        }

        Map<Desfecho, Integer> porDesfecho = new EnumMap<>(Desfecho.class);
        desfechos.forEach(desfecho -> porDesfecho.merge(desfecho, 1, Integer::sum));

        return new ContagemDeAcuracia(
                porDesfecho.getOrDefault(Desfecho.VERDADEIRO_POSITIVO, 0),
                porDesfecho.getOrDefault(Desfecho.FALSO_POSITIVO, 0),
                porDesfecho.getOrDefault(Desfecho.FALSO_NEGATIVO, 0),
                porDesfecho.getOrDefault(Desfecho.VERDADEIRO_NEGATIVO, 0),
                porDesfecho.getOrDefault(Desfecho.NAO_AVALIADO, 0),
                porDesfecho.getOrDefault(Desfecho.SEM_AVALIACAO, 0));
    }

    /** Soma duas contagens, para consolidar regras num resultado só. */
    public ContagemDeAcuracia mais(ContagemDeAcuracia outra) {
        if (outra == null) {
            throw new AcuraciaInvalida("Não há contagem a somar.");
        }
        return new ContagemDeAcuracia(
                verdadeirosPositivos + outra.verdadeirosPositivos,
                falsosPositivos + outra.falsosPositivos,
                falsosNegativos + outra.falsosNegativos,
                verdadeirosNegativos + outra.verdadeirosNegativos,
                naoAvaliados + outra.naoAvaliados,
                semAvaliacao + outra.semAvaliacao);
    }

    /** Linhas do gabarito que o motor julgou: os quatro quadrantes da matriz. */
    public int avaliados() {
        return verdadeirosPositivos + falsosPositivos + falsosNegativos + verdadeirosNegativos;
    }

    /** Linhas do gabarito consideradas, julgadas ou não. */
    public int total() {
        return avaliados() + naoAvaliados + semAvaliacao;
    }

    /**
     * VP sobre VP mais FP: do que o motor apontou e foi medido, quanto procedia.
     *
     * <p>Indefinida quando o motor não apontou nada que tenha sido medido.</p>
     */
    public Metrica precisao() {
        return Metrica.razao(
                verdadeirosPositivos,
                (long) verdadeirosPositivos + falsosPositivos,
                "o motor não produziu nenhum apontamento entre as linhas medidas (VP + FP = 0). "
                        + "Precisão não é 1 aqui: não há apontamento sobre o qual afirmar acerto.");
    }

    /**
     * VP sobre VP mais FN: do que o gabarito diz haver, quanto o motor achou.
     *
     * <p>Indefinido quando nenhuma linha medida foi rotulada como ACHADO.</p>
     */
    public Metrica recall() {
        return Metrica.razao(
                verdadeirosPositivos,
                (long) verdadeirosPositivos + falsosNegativos,
                "o gabarito não rotulou nenhuma linha medida como ACHADO (VP + FN = 0). Recall não é "
                        + "1 aqui: não há incoerência conhecida cuja detecção se possa afirmar.");
    }

    /**
     * Média harmônica de precisão e recall, definida só onde as duas são.
     *
     * <p>A fórmula e o motivo da restrição estão na descrição deste tipo.</p>
     */
    public Metrica f1() {
        if (!precisao().estaDefinida() || !recall().estaDefinida()) {
            return Metrica.indefinida(
                    "F1 resume precisão e recall, e ao menos um dos dois é indefinido aqui.");
        }
        long duasVezesVp = 2L * verdadeirosPositivos;
        return Metrica.razao(
                duasVezesVp,
                duasVezesVp + falsosPositivos + falsosNegativos,
                "não há VP, FP nem FN a resumir.");
    }

    /**
     * Avaliados sobre o total do gabarito: quanto do que se pediu para julgar
     * foi julgado.
     *
     * <p>É a métrica que carrega o peso dos {@code NAO_AVALIADO}. Sem ela,
     * precisão e recall altos sobre três linhas de trezentas pareceriam um bom
     * resultado.</p>
     */
    public Metrica cobertura() {
        return Metrica.razao(
                avaliados(),
                total(),
                "o gabarito não traz nenhuma linha para esta regra.");
    }

    /** Quantas vezes o desfecho indicado ocorreu. */
    public int quantidadeDe(Desfecho desfecho) {
        if (desfecho == null) {
            throw new AcuraciaInvalida("Não há desfecho cuja contagem consultar.");
        }
        return switch (desfecho) {
            case VERDADEIRO_POSITIVO -> verdadeirosPositivos;
            case FALSO_POSITIVO -> falsosPositivos;
            case FALSO_NEGATIVO -> falsosNegativos;
            case VERDADEIRO_NEGATIVO -> verdadeirosNegativos;
            case NAO_AVALIADO -> naoAvaliados;
            case SEM_AVALIACAO -> semAvaliacao;
        };
    }

    private static void exigirNaoNegativo(int valor, String nomeDoCampo) {
        if (valor < 0) {
            throw new AcuraciaInvalida(
                    "A contagem \"%s\" não pode ser negativa, mas veio %d.".formatted(nomeDoCampo, valor));
        }
    }
}
