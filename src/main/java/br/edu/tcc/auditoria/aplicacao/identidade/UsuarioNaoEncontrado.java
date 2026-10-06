package br.edu.tcc.auditoria.aplicacao.identidade;

public class UsuarioNaoEncontrado extends IdentidadeInvalida {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public UsuarioNaoEncontrado(String mensagem) {
        super(mensagem);
    }
}
