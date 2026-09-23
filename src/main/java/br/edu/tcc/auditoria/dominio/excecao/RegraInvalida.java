package br.edu.tcc.auditoria.dominio.excecao;

public class RegraInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public RegraInvalida(String mensagem) {
        super(mensagem);
    }
}
