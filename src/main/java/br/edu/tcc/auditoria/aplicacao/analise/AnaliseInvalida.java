package br.edu.tcc.auditoria.aplicacao.analise;

public class AnaliseInvalida extends RuntimeException {
    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AnaliseInvalida(String mensagem) {
        super(mensagem);
    }
}
