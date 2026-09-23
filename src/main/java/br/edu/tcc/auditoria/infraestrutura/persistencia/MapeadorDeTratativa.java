package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

// Classe que converte a linha gravada de tratativa na tratativa do domínio.
final class MapeadorDeTratativa {

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private MapeadorDeTratativa() {
    }

    // Método estático que monta a tratativa do domínio a partir da linha gravada.
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
