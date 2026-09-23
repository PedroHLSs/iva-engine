package br.edu.tcc.auditoria.dominio.excecao;

public class ChaveAcessoInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ChaveAcessoInvalida(String mensagem) {
        super(mensagem);
    }
}
