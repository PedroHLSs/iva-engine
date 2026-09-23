package br.edu.tcc.auditoria.infraestrutura.configuracao;

public class ConfiguracaoInvalida extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ConfiguracaoInvalida(String mensagem) {
        super(mensagem);
    }

    // Construtor que recebe a mensagem de erro e a causa original da falha.
    public ConfiguracaoInvalida(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
