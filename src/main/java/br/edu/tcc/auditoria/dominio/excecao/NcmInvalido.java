package br.edu.tcc.auditoria.dominio.excecao;

public class NcmInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public NcmInvalido(String mensagem) {
        super(mensagem);
    }
}
