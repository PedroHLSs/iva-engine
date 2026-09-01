package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

/** Tradução entre tratativa do domínio e linha gravada. */
final class MapeadorDeTratativa {

    private MapeadorDeTratativa() {
    }

    static Tratativa paraDominio(TratativaEntidade entidade) {
        return new Tratativa(
                new ChaveDeTratativa(
                        new HashDoItem(entidade.hashItem()),
                        entidade.regraId(),
                        entidade.regraVersao()),
                entidade.decisao(),
                entidade.justificativa(),
                entidade.registradoEm());
    }
}
