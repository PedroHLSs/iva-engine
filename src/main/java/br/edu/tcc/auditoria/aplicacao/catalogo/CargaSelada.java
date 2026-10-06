package br.edu.tcc.auditoria.aplicacao.catalogo;

public class CargaSelada extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CargaSelada(String mensagem) {
        super(mensagem);
    }
}
