package br.edu.tcc.auditoria.dominio.excecao;

public class ValorEmRiscoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ValorEmRiscoInvalido(String mensagem) {
        super(mensagem);
    }
}
