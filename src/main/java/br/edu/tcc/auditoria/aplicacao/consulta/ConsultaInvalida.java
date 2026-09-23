package br.edu.tcc.auditoria.aplicacao.consulta;

public class ConsultaInvalida extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ConsultaInvalida(String mensagem) {
        super(mensagem);
    }
}
