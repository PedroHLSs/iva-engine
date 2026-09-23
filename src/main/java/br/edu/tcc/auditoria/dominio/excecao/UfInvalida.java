package br.edu.tcc.auditoria.dominio.excecao;

public class UfInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public UfInvalida(String mensagem) {
        super(mensagem);
    }
}
