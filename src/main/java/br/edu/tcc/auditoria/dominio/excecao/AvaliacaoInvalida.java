package br.edu.tcc.auditoria.dominio.excecao;

public class AvaliacaoInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AvaliacaoInvalida(String mensagem) {
        super(mensagem);
    }
}
