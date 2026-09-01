package br.edu.tcc.auditoria.aplicacao.auditoria;

/**
 * Porta de gravação de uma rodada de auditoria.
 *
 * <p>Grava tudo de uma vez — documentos, itens, apontamentos e o recibo da
 * execução — porque tudo isso é um único fato: "este lote foi auditado assim,
 * nesta hora". Persistir em partes admitiria estado intermediário em que a
 * execução existe mas os apontamentos dela não, ou o contrário.</p>
 *
 * <h2>Reprocessar não duplica</h2>
 *
 * <p>Quem implementa deve tratar apontamento repetido como o mesmo apontamento.
 * A identidade é a chave de tratativa — resumo do item, identificador da regra e
 * versão da regra. Rodar o mesmo lote duas vezes registra duas execuções, e cada
 * execução conta seus apontamentos; mas o apontamento em si continua sendo um só,
 * agora sabendo que foi visto de novo. É o que faz a tratativa dada por uma
 * pessoa continuar valendo depois do reprocessamento.</p>
 */
public interface RepositorioDaAuditoria {

    /** Grava a rodada inteira. */
    void persistir(ResultadoDaAuditoria resultado);
}
