package br.edu.tcc.auditoria.dominio;

/**
 * Desfecho da aplicação de uma regra de auditoria sobre um documento ou item.
 *
 * <p>Os três estados são exaustivos e mutuamente exclusivos. A existência de
 * {@link #NAO_AVALIADO} é o que permite ao relatório não mentir: quando falta o
 * dado ou falta a tabela normativa, a regra não conclui nada, e isso é
 * diferente de concluir que está tudo certo.</p>
 */
public enum ResultadoAvaliacao {

    /** A regra encontrou incoerência. Gera {@link Achado}. */
    ACHADO,

    /** A regra foi aplicada por inteiro e não encontrou incoerência. */
    CONFORME,

    /**
     * A regra não pôde ser aplicada — faltou campo no documento, faltou a
     * tabela normativa correspondente, ou a data do documento está fora da
     * vigência da regra.
     *
     * <p>Nunca deve ser reportado como conformidade.</p>
     */
    NAO_AVALIADO
}
