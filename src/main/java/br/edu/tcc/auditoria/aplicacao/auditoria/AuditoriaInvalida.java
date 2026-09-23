package br.edu.tcc.auditoria.aplicacao.auditoria;


public class AuditoriaInvalida extends RuntimeException {
    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AuditoriaInvalida(String mensagem) {
        super(mensagem);
    }
}
