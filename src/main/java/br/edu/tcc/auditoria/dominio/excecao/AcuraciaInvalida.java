package br.edu.tcc.auditoria.dominio.excecao;

public class AcuraciaInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AcuraciaInvalida(String mensagem) {
        super(mensagem);
    }
}
