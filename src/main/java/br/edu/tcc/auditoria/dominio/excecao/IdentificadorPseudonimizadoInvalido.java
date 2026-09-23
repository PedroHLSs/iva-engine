package br.edu.tcc.auditoria.dominio.excecao;

public class IdentificadorPseudonimizadoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public IdentificadorPseudonimizadoInvalido(String mensagem) {
        super(mensagem);
    }
}
