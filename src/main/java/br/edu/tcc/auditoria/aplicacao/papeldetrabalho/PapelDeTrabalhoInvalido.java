package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

public class PapelDeTrabalhoInvalido extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public PapelDeTrabalhoInvalido(String mensagem) {
        super(mensagem);
    }

    // Construtor que recebe a mensagem de erro e a causa original da falha.
    public PapelDeTrabalhoInvalido(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
