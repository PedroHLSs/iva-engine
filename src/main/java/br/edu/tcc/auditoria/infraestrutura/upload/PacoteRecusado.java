package br.edu.tcc.auditoria.infraestrutura.upload;

/**
 * O que chegou não vai ser processado, e a mensagem diz por quê.
 *
 * <p>É recusa, não queda: o arquivo enviado é entrada não confiável, e recusar
 * na fronteira é o comportamento correto. A mensagem é para a pessoa que enviou,
 * então diz o que fazer — mandar {@code .zip} em vez de {@code .rar}, dividir o
 * lote, tirar o arquivo que não é documento.</p>
 */
public class PacoteRecusado extends RuntimeException {

    public PacoteRecusado(String mensagem) {
        super(mensagem);
    }

    public PacoteRecusado(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
