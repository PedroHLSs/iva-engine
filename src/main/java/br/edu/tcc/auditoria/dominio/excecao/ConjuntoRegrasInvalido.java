package br.edu.tcc.auditoria.dominio.excecao;

public class ConjuntoRegrasInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ConjuntoRegrasInvalido(String mensagem) {
        super(mensagem);
    }
}
