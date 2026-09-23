package br.edu.tcc.auditoria.dominio.excecao;

public class RegistroNormativoInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public RegistroNormativoInvalido(String mensagem) {
        super(mensagem);
    }
}
