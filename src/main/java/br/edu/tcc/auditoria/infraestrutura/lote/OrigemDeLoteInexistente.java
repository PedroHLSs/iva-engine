package br.edu.tcc.auditoria.infraestrutura.lote;

public class OrigemDeLoteInexistente extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public OrigemDeLoteInexistente(String mensagem) {
        super(mensagem);
    }
}
