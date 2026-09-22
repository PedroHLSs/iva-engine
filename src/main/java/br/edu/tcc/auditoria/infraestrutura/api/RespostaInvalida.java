package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * Resposta que violaria a própria disciplina de representação da API.
 *
 * <p>Não é erro de quem consulta: é erro de quem montou a resposta. Todo DTO
 * desta camada valida no construtor que ausência veio acompanhada do motivo, e
 * é esta a exceção que os construtores lançam. Ela sinaliza defeito de
 * programação, e por isso sai como 500 — diferente de {@code PedidoInvalido},
 * que é 400.</p>
 */
public class RespostaInvalida extends RuntimeException {

    public RespostaInvalida(String mensagem) {
        super(mensagem);
    }
}
