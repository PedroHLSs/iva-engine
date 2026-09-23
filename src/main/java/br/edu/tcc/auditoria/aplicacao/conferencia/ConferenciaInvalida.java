package br.edu.tcc.auditoria.aplicacao.conferencia;

public class ConferenciaInvalida extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ConferenciaInvalida(String mensagem) {
        super(mensagem);
    }
}
