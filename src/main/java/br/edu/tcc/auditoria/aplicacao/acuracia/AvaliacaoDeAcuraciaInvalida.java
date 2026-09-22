package br.edu.tcc.auditoria.aplicacao.acuracia;

public class AvaliacaoDeAcuraciaInvalida extends RuntimeException {
    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AvaliacaoDeAcuraciaInvalida(String mensagem) {
        super(mensagem);
    }
}
