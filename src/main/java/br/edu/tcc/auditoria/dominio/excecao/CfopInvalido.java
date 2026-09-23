package br.edu.tcc.auditoria.dominio.excecao;

public class CfopInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CfopInvalido(String mensagem) {
        super(mensagem);
    }
}
