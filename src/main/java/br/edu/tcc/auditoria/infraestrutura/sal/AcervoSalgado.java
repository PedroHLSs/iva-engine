package br.edu.tcc.auditoria.infraestrutura.sal;

/**
 * O que no banco depende do sal, e portanto deixa de fazer sentido quando ele
 * muda.
 *
 * <h2>O que é salgado, e o que não é</h2>
 *
 * <p>Salgado é o documento: {@code emitente_pseudonimizado} e
 * {@code destinatario_pseudonimizado} saem do sal. As execuções e os
 * apontamentos vão junto por dependerem dos documentos, não por serem salgados.</p>
 *
 * <p><strong>Tratativa não é salgada e não é apagada.</strong> A chave dela é
 * {@code (hash do item, regra, versão da regra)}, e o hash do item não leva sal —
 * é identidade reproduzível entre instalações, não sigilo. Uma decisão humana
 * registrada continua válida depois da troca de sal, e se reaplica sozinha
 * quando o mesmo lote for reprocessado. Apagá-la aqui destruiria trabalho de
 * auditoria que o problema do sal nunca tocou.</p>
 */
public interface AcervoSalgado {

    /**
     * Quantos documentos existem.
     *
     * <p>É a medida de "o banco já tem dados" que interessa ao guarda. Catálogo
     * importado não entra: ele não tem sal nenhum, e travar a subida por causa
     * dele seria recusar por um motivo que não existe.</p>
     */
    long quantidadeDeDocumentos();

    /**
     * Apaga documentos, itens, execuções e apontamentos, preservando tratativas.
     *
     * @return o que foi apagado, para o comando poder dizer o que fez
     */
    Apagamento apagar();

    /** O que o apagamento removeu, e o que deliberadamente não removeu. */
    record Apagamento(long documentos, long execucoes, long tratativasPreservadas) {
    }
}
