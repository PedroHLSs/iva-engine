package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import java.util.Optional;

// Enum com os seis desfechos do confronto entre gabarito e motor; só os quatro primeiros entram em precisão e recall.
public enum Desfecho {

    // Gabarito diz ACHADO, motor apontou.
    VERDADEIRO_POSITIVO,

    // Gabarito diz CONFORME, motor apontou: apontamento que não procede.
    FALSO_POSITIVO,

    // Gabarito diz ACHADO, motor concluiu conforme: incoerência que passou.
    FALSO_NEGATIVO,

    // Gabarito diz CONFORME, motor concluiu conforme.
    VERDADEIRO_NEGATIVO,

    // O motor não pôde julgar; fica fora de precisão e recall e aparece na cobertura.
    NAO_AVALIADO,

    // O motor não produziu avaliação para esta linha do gabarito: gabarito e acervo estão desalinhados.
    SEM_AVALIACAO;

    // Indica se o desfecho entra na matriz de confusão; não tem chamador em produção, e um teste confronta o que ele afirma com o que ContagemDeAcuracia conta.
    public boolean entraNaMetrica() {
        return this == VERDADEIRO_POSITIVO
                || this == FALSO_POSITIVO
                || this == FALSO_NEGATIVO
                || this == VERDADEIRO_NEGATIVO;
    }

    // Método estático que confronta o rótulo do gabarito com o desfecho do motor; sem avaliação correspondente, o resultado é SEM_AVALIACAO.
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
