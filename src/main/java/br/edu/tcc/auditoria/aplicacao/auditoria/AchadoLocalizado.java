package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

/**
 * Um apontamento junto do resumo do item que o originou.
 *
 * <p>O apontamento do domínio identifica seu alvo por chave de acesso e número
 * de item, que é o que uma pessoa lê no relatório. Para gravar e reencontrar o
 * apontamento entre execuções falta o resumo do conteúdo do item, e é ele que
 * este par acrescenta — ver {@link HashDoItem}.</p>
 */
public record AchadoLocalizado(Achado achado, HashDoItem hashDoItem) {

    public AchadoLocalizado {
        if (achado == null) {
            throw new AuditoriaInvalida("Não há apontamento a localizar.");
        }
        if (hashDoItem == null) {
            throw new AuditoriaInvalida(
                    "O apontamento precisa do resumo do item que o originou: sem ele a tratativa dada "
                            + "por uma pessoa não sobrevive ao reprocessamento do lote.");
        }
    }

    /** Chave da tratativa que se aplica a este apontamento. */
    public ChaveDeTratativa chaveDeTratativa() {
        return ChaveDeTratativa.de(hashDoItem, achado);
    }
}
