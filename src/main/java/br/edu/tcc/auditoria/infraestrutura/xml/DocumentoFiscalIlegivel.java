package br.edu.tcc.auditoria.infraestrutura.xml;

public class DocumentoFiscalIlegivel extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public DocumentoFiscalIlegivel(String mensagem) {
        super(mensagem);
    }

    // Construtor que recebe a mensagem de erro e a causa original da falha.
    public DocumentoFiscalIlegivel(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
