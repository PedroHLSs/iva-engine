package br.edu.tcc.auditoria.infraestrutura.api;

public class RespostaInvalida extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public RespostaInvalida(String mensagem) {
        super(mensagem);
    }
}
