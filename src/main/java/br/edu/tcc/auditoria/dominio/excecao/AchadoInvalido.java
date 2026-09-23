package br.edu.tcc.auditoria.dominio.excecao;

public class AchadoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public AchadoInvalido(String mensagem) {
        super(mensagem);
    }
}
