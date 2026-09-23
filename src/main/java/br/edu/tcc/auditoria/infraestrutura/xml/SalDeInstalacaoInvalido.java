package br.edu.tcc.auditoria.infraestrutura.xml;

public class SalDeInstalacaoInvalido extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public SalDeInstalacaoInvalido(String mensagem) {
        super(mensagem);
    }
}
