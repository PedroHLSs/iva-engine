package br.edu.tcc.auditoria.infraestrutura.cli;

/**
 * Comando chamado de forma que não dá para executar.
 *
 * <p>Distinta das demais exceções porque tem tratamento distinto: erro de uso
 * imprime a mensagem e o modo de usar, sem rastro de pilha. Quem digitou o
 * caminho errado não precisa ver a pilha de chamadas do Spring.</p>
 */
class UsoInvalido extends RuntimeException {

    UsoInvalido(String mensagem) {
        super(mensagem);
    }
}
