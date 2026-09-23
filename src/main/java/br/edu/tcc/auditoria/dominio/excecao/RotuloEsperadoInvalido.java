package br.edu.tcc.auditoria.dominio.excecao;

public class RotuloEsperadoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public RotuloEsperadoInvalido(String mensagem) {
        super(mensagem);
    }
}
