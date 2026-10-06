package br.edu.tcc.auditoria.aplicacao.identidade;

public class UltimoAdministrador extends IdentidadeInvalida {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public UltimoAdministrador(String mensagem) {
        super(mensagem);
    }
}
