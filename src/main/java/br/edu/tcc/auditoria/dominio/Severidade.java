package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.SeveridadeInvalida;

// Enum com a gravidade de um apontamento, da mais grave para a menos grave. Mede o efeito na nota, não a consequência jurídica.
public enum Severidade {

    // O grupo de IBS/CBS do item não dá para interpretar: falta campo necessário ou o código não existe nas tabelas.
    CRITICA,

    // Problema que mexe com valor: base, alíquota ou valor do tributo que não batem.
    GRAVE,

    // Problema entre campos que não muda valor, como combinação de códigos que a tabela não aceita.
    MODERADA,

    // Observação sem afirmar erro: situação que merece o olhar de uma pessoa.
    INFORMATIVA;

    // Indica se esta gravidade é maior que a outra, pela ordem em que estão declaradas. Ainda não é usado em produção.
    public boolean maisGraveQue(Severidade outra) {
        if (outra == null) {
            throw new SeveridadeInvalida(
                    "A severidade comparada não pode ser nula: não há gravidade a confrontar.");
        }
        return this.ordinal() < outra.ordinal();
    }
}
