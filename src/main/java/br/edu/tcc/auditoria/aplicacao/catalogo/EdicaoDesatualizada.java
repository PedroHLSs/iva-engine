package br.edu.tcc.auditoria.aplicacao.catalogo;

public class EdicaoDesatualizada extends RuntimeException {

    private final transient PreviaDaEdicao previaAtual;

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public EdicaoDesatualizada(String mensagem, PreviaDaEdicao previaAtual) {
        super(mensagem);
        this.previaAtual = previaAtual;
    }

    public PreviaDaEdicao previaAtual() {
        return previaAtual;
    }
}
