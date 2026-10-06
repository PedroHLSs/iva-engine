package br.edu.tcc.auditoria.aplicacao.catalogo;

public class CargaEsperadaMudou extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CargaEsperadaMudou(String mensagem) {
        super(mensagem);
    }
}
