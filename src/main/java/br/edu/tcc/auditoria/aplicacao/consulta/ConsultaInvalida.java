package br.edu.tcc.auditoria.aplicacao.consulta;

/** Consulta de apontamentos malformada, ou resultado de consulta incompleto. */
public class ConsultaInvalida extends RuntimeException {

    public ConsultaInvalida(String mensagem) {
        super(mensagem);
    }
}
