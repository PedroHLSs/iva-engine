package br.edu.tcc.auditoria.dominio.excecao;

public class CodigoClassificacaoTributariaInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CodigoClassificacaoTributariaInvalido(String mensagem) {
        super(mensagem);
    }
}
