package br.edu.tcc.auditoria.dominio.tratativa;

/**
 * Decisão registrada por uma pessoa sobre um apontamento.
 *
 * <p>Só há duas, e nenhuma delas apaga o apontamento: o relatório continua
 * mostrando que a incoerência foi levantada, agora acompanhada do que se
 * concluiu sobre ela. O sistema aponta; quem decide é o auditor.</p>
 */
public enum DecisaoDeTratativa {

    /** O auditor concorda com o apontamento: há incoerência no documento. */
    ACEITO,

    /**
     * O auditor sustenta que o documento está correto e o apontamento não
     * procede — leitura errada da regra, tabela de referência incompleta, ou
     * situação que a regra não distingue.
     */
    REFUTADO
}
