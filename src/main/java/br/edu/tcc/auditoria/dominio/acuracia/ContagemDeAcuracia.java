package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

// Representa a matriz de confusão de uma regra, mais não avaliados e sem avaliação; precisão e recall usam só os quatro quadrantes, e esses dois ficam na cobertura.
public record ContagemDeAcuracia(
        int verdadeirosPositivos,
        int falsosPositivos,
        int falsosNegativos,
        int verdadeirosNegativos,
        int naoAvaliados,
        int semAvaliacao) {

    // Valida que nenhuma das seis contagens seja negativa.
    public ContagemDeAcuracia {
        exigirNaoNegativo(verdadeirosPositivos, "verdadeirosPositivos");
        exigirNaoNegativo(falsosPositivos, "falsosPositivos");
        exigirNaoNegativo(falsosNegativos, "falsosNegativos");
        exigirNaoNegativo(verdadeirosNegativos, "verdadeirosNegativos");
        exigirNaoNegativo(naoAvaliados, "naoAvaliados");
        exigirNaoNegativo(semAvaliacao, "semAvaliacao");
    }

    // Método estático que retorna a contagem zerada, ponto de partida das somas.
    public static ContagemDeAcuracia nenhuma() {
        return new ContagemDeAcuracia(0, 0, 0, 0, 0, 0);
    }

    // Método estático que conta os desfechos de um confronto inteiro.
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

    // Soma duas contagens, para consolidar as regras num resultado só.
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

    // Retorna as linhas que o motor julgou: a soma dos quatro quadrantes da matriz.
    public int avaliados() {
        return verdadeirosPositivos + falsosPositivos + falsosNegativos + verdadeirosNegativos;
    }

    // Retorna as linhas do gabarito consideradas, julgadas ou não.
    public int total() {
        return avaliados() + naoAvaliados + semAvaliacao;
    }

    // Retorna a precisão, VP / (VP + FP); indefinida quando o motor não apontou nada entre as linhas medidas.
    public Metrica precisao() {
        return Metrica.razao(
                verdadeirosPositivos,
                (long) verdadeirosPositivos + falsosPositivos,
                "o motor não produziu nenhum apontamento entre as linhas medidas (VP + FP = 0). "
                        + "Precisão não é 1 aqui: não há apontamento sobre o qual afirmar acerto.");
    }

    // Retorna o recall, VP / (VP + FN); indefinido quando nenhuma linha medida foi rotulada como ACHADO.
    public Metrica recall() {
        return Metrica.razao(
                verdadeirosPositivos,
                (long) verdadeirosPositivos + falsosNegativos,
                "o gabarito não rotulou nenhuma linha medida como ACHADO (VP + FN = 0). Recall não é "
                        + "1 aqui: não há incoerência conhecida cuja detecção se possa afirmar.");
    }

    // Retorna o F1, calculado como 2VP / (2VP + FP + FN), definido só quando precisão e recall também são.
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

    // Retorna a cobertura, avaliados sobre o total do gabarito, que é onde os não avaliados pesam.
    public Metrica cobertura() {
        return Metrica.razao(
                avaliados(),
                total(),
                "o gabarito não traz nenhuma linha para esta regra.");
    }

    // Retorna quantas vezes o desfecho indicado ocorreu.
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

    // Método auxiliar para verificar se uma contagem é negativa e lançar uma exceção.
    private static void exigirNaoNegativo(int valor, String nomeDoCampo) {
        if (valor < 0) {
            throw new AcuraciaInvalida(
                    "A contagem \"%s\" não pode ser negativa, mas veio %d.".formatted(nomeDoCampo, valor));
        }
    }
}
