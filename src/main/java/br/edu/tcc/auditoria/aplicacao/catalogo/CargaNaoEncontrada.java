package br.edu.tcc.auditoria.aplicacao.catalogo;

public class CargaNaoEncontrada extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CargaNaoEncontrada(String mensagem) {
        super(mensagem);
    }
}
