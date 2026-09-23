package br.edu.tcc.auditoria.infraestrutura.sal;

public class SalTrocado extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public SalTrocado(String mensagem) {
        super(mensagem);
    }
}
