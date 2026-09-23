package br.edu.tcc.auditoria.dominio.excecao;

public class CodigoCstInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CodigoCstInvalido(String mensagem) {
        super(mensagem);
    }
}
