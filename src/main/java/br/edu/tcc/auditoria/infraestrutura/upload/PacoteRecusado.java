package br.edu.tcc.auditoria.infraestrutura.upload;

public class PacoteRecusado extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public PacoteRecusado(String mensagem) {
        super(mensagem);
    }

    // Construtor que recebe a mensagem de erro e a causa original da falha.
    public PacoteRecusado(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
