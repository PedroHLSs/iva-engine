package br.edu.tcc.auditoria.dominio.excecao;

public class DocumentoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public DocumentoInvalido(String mensagem) {
        super(mensagem);
    }
}
