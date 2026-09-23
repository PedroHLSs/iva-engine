package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

//Representa um erro  que foi localizado, contendo o achado e o hash do item correspondente.
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

    public ChaveDeTratativa chaveDeTratativa() {
        return ChaveDeTratativa.de(hashDoItem, achado);
    }
}
