package br.edu.tcc.auditoria.infraestrutura.api;

/** Parâmetro de consulta que a API não sabe interpretar. Sai como 400. */
public class PedidoInvalido extends RuntimeException {

    public PedidoInvalido(String mensagem) {
        super(mensagem);
    }
}
