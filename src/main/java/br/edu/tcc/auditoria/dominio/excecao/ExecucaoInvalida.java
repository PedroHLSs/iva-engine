package br.edu.tcc.auditoria.dominio.excecao;

public class ExecucaoInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ExecucaoInvalida(String mensagem) {
        super(mensagem);
    }
}
