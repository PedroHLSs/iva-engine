package br.edu.tcc.auditoria.dominio.excecao;

public class ItemDocumentoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ItemDocumentoInvalido(String mensagem) {
        super(mensagem);
    }
}
