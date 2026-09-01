package br.edu.tcc.auditoria.infraestrutura.cli;

/**
 * Para onde os comandos escrevem.
 *
 * <p>É uma interface, e não {@code System.out} espalhado pelos comandos, para
 * que o texto produzido possa ser conferido em teste sem capturar a saída do
 * processo.</p>
 */
interface Saida {

    /** Escreve uma linha. */
    void linha(String texto);

    /** Escreve uma linha em branco. */
    default void linhaEmBranco() {
        linha("");
    }

    /** Escreve uma linha formatada. */
    default void linha(String formato, Object... valores) {
        linha(formato.formatted(valores));
    }
}
