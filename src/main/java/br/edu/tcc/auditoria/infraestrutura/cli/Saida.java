package br.edu.tcc.auditoria.infraestrutura.cli;

// Interface para onde os comandos escrevem, para o texto poder ser conferido em teste sem capturar a saída do processo.
interface Saida {

    // Escreve uma linha.
    void linha(String texto);

    // Escreve uma linha em branco.
    default void linhaEmBranco() {
        linha("");
    }

    // Escreve uma linha formatada.
    default void linha(String formato, Object... valores) {
        linha(formato.formatted(valores));
    }
}
