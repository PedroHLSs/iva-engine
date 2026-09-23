package br.edu.tcc.auditoria.dominio.excecao;

public class CatalogoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CatalogoInvalido(String mensagem) {
        super(mensagem);
    }
}
