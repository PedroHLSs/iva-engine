package br.edu.tcc.auditoria.aplicacao.identidade;

public class IdentidadeInvalida extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public IdentidadeInvalida(String mensagem) {
        super(mensagem);
    }
}
