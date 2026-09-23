package br.edu.tcc.auditoria.dominio.excecao;

public class SeveridadeInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public SeveridadeInvalida(String mensagem) {
        super(mensagem);
    }
}
