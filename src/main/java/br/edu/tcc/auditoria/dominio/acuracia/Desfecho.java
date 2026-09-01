package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import java.util.Optional;

/**
 * O confronto de uma linha do gabarito com o que o motor concluiu.
 *
 * <p>Seis desfechos, dos quais <strong>apenas quatro</strong> entram em precisão
 * e recall. Os outros dois são o motivo de este tipo existir em vez de um
 * {@code boolean acertou}.</p>
 *
 * <h2>Os dois que não são acerto nem erro</h2>
 *
 * <p>{@link #NAO_AVALIADO} é a regra dizendo que não pôde julgar — faltou campo
 * no documento ou faltou tabela no catálogo. Contá-lo como erro puniria o
 * sistema por ser honesto; contá-lo como acerto seria pior, porque um sistema
 * que não avalia nada teria acurácia perfeita. Ele fica fora das duas contas e
 * aparece na cobertura.</p>
 *
 * <p>{@link #SEM_AVALIACAO} é o gabarito apontando para um item que o motor nem
 * viu — documento fora do lote, item que não existe naquele documento. Não diz
 * nada sobre a qualidade das regras: diz que o gabarito e o acervo não estão
 * alinhados, e quem mediu precisa saber disso antes de olhar qualquer
 * número.</p>
 */
public enum Desfecho {

    /** Gabarito diz ACHADO, motor apontou. */
    VERDADEIRO_POSITIVO,

    /** Gabarito diz CONFORME, motor apontou: apontamento que não procede. */
    FALSO_POSITIVO,

    /** Gabarito diz ACHADO, motor concluiu conforme: incoerência que passou. */
    FALSO_NEGATIVO,

    /** Gabarito diz CONFORME, motor concluiu conforme. */
    VERDADEIRO_NEGATIVO,

    /** O motor não pôde julgar. Fora de precisão e de recall, por construção. */
    NAO_AVALIADO,

    /** O motor não produziu avaliação para esta linha do gabarito. */
    SEM_AVALIACAO;

    /**
     * Indica se este desfecho entra na matriz de confusão.
     *
     * <p>Verdadeiro apenas para os quatro primeiros. É o único ponto do sistema
     * em que se decide o que conta como medição, e existe isolado justamente
     * para que a decisão não fique espalhada em somas pelo código.</p>
     */
    public boolean entraNaMetrica() {
        return this == VERDADEIRO_POSITIVO
                || this == FALSO_POSITIVO
                || this == FALSO_NEGATIVO
                || this == VERDADEIRO_NEGATIVO;
    }

    /**
     * Confronta o rótulo do gabarito com o desfecho do motor.
     *
     * @param esperado o que a pessoa que rotulou afirmou
     * @param obtido   o que o motor concluiu; vazio quando não houve avaliação
     *                 correspondente àquela linha do gabarito
     */
    public static Desfecho de(RotuloEsperado esperado, Optional<ResultadoAvaliacao> obtido) {
        if (esperado == null) {
            throw new AcuraciaInvalida(
                    "Não há rótulo esperado a confrontar: sem gabarito não há o que medir.");
        }
        if (obtido == null) {
            throw new AcuraciaInvalida(
                    "A ausência de avaliação do motor se representa com Optional.empty(), nunca com nulo.");
        }
        if (obtido.isEmpty()) {
            return SEM_AVALIACAO;
        }
        return switch (obtido.get()) {
            case NAO_AVALIADO -> NAO_AVALIADO;
            case ACHADO -> switch (esperado) {
                case ACHADO -> VERDADEIRO_POSITIVO;
                case CONFORME -> FALSO_POSITIVO;
            };
            case CONFORME -> switch (esperado) {
                case ACHADO -> FALSO_NEGATIVO;
                case CONFORME -> VERDADEIRO_NEGATIVO;
            };
        };
    }
}
