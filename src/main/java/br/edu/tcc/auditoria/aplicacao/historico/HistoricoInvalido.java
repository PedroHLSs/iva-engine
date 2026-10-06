package br.edu.tcc.auditoria.aplicacao.historico;

public class HistoricoInvalido extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public HistoricoInvalido(String mensagem) {
        super(mensagem);
    }
}
