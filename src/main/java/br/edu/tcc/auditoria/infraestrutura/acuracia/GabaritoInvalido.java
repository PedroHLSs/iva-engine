package br.edu.tcc.auditoria.infraestrutura.acuracia;

public class GabaritoInvalido extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public GabaritoInvalido(String mensagem) {
        super(mensagem);
    }

    // Construtor que recebe a mensagem de erro e a causa original da falha.
    public GabaritoInvalido(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
