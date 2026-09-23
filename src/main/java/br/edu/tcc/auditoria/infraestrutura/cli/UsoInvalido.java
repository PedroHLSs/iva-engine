package br.edu.tcc.auditoria.infraestrutura.cli;

class UsoInvalido extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    UsoInvalido(String mensagem) {
        super(mensagem);
    }
}
