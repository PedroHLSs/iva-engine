package br.edu.tcc.auditoria.infraestrutura.api;

public class PedidoInvalido extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public PedidoInvalido(String mensagem) {
        super(mensagem);
    }
}
