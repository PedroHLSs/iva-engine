package br.edu.tcc.auditoria.aplicacao.conferencia;

/**
 * Tradução de desfecho impossível, ou situação de produto montada sem o que
 * precisa para ser afirmada.
 *
 * <p>Existe para que a camada de conferência recuse em voz alta em vez de
 * escolher um estado por conta própria. Um estado escolhido por omissão é a
 * forma como esta camada mentiria: a interface diria "sem divergência" sobre um
 * produto que ninguém julgou.</p>
 */
public class ConferenciaInvalida extends RuntimeException {

    public ConferenciaInvalida(String mensagem) {
        super(mensagem);
    }
}
