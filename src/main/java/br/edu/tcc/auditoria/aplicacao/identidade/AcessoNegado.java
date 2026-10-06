package br.edu.tcc.auditoria.aplicacao.identidade;

public class AcessoNegado extends IdentidadeInvalida {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AcessoNegado(String mensagem) {
        super(mensagem);
    }
}
