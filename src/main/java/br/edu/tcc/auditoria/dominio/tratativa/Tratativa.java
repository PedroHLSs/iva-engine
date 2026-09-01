package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;

import java.time.Instant;

/**
 * Decisão humana sobre um apontamento, com a razão registrada.
 *
 * <p><strong>A justificativa é obrigatória.</strong> Uma tratativa sem razão
 * escrita transforma o relatório numa lista de apontamentos silenciados sem que
 * se saiba por quê — o oposto do que uma auditoria produz. O construtor recusa
 * texto vazio ou só com espaço.</p>
 *
 * <p>A tratativa é presa à {@link ChaveDeTratativa}, isto é, ao conteúdo do item
 * e à versão da regra, e não à linha do apontamento no banco. É isso que a faz
 * sobreviver ao reprocessamento do lote e reabrir quando a regra muda de versão
 * — ver {@link ChaveDeTratativa}.</p>
 *
 * @param chave         o item e a regra tratados
 * @param decisao       o que foi decidido
 * @param justificativa por quê, em texto livre e obrigatório
 * @param registradoEm  quando a decisão foi registrada
 */
public record Tratativa(
        ChaveDeTratativa chave,
        DecisaoDeTratativa decisao,
        String justificativa,
        Instant registradoEm) {

    public Tratativa {
        if (chave == null) {
            throw new TratativaInvalida("A tratativa precisa dizer o que está tratando.");
        }
        if (decisao == null) {
            throw new TratativaInvalida(
                    "A tratativa precisa de decisão: %s ou %s."
                            .formatted(DecisaoDeTratativa.ACEITO, DecisaoDeTratativa.REFUTADO));
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new TratativaInvalida(
                    "A tratativa precisa de justificativa. Apontamento tratado sem razão registrada é "
                            + "apontamento apagado, e o relatório deixa de dizer por que a incoerência "
                            + "não é mais incoerência.");
        }
        if (registradoEm == null) {
            throw new TratativaInvalida("A tratativa precisa registrar quando foi dada.");
        }
    }

    /** Indica se esta tratativa responde ao apontamento indicado, para o item indicado. */
    public boolean seAplicaA(Achado achado, HashDoItem hashDoItem) {
        return chave.equals(ChaveDeTratativa.de(hashDoItem, achado));
    }

    /** Atalho para {@code chave().hashDoItem()}. */
    public HashDoItem hashDoItem() {
        return chave.hashDoItem();
    }

    /** Atalho para {@code chave().regraId()}. */
    public String regraId() {
        return chave.regraId();
    }

    /** Atalho para {@code chave().regraVersao()}. */
    public String regraVersao() {
        return chave.regraVersao();
    }
}
