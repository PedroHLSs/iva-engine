package br.edu.tcc.auditoria.dominio;

/**
 * Gravidade atribuída a um {@link Achado}, para ordenar o relatório.
 *
 * <p>O critério de separação é o efeito da incoerência sobre o documento, não a
 * consequência jurídica dela: o sistema aponta, não aconselha. As constantes
 * estão declaradas da mais grave para a menos grave.</p>
 */
public enum Severidade {

    /**
     * O grupo de IBS/CBS do item ficou ininterpretável: campo estruturalmente
     * necessário ausente, ou código que não existe em nenhuma tabela
     * importada. Não há como afirmar sequer qual tratamento foi pretendido.
     */
    CRITICA,

    /**
     * Incoerência que afeta valor declarado — base, alíquota ou valor de
     * tributo que não fecham entre si ou não fecham com a tabela de
     * referência.
     */
    GRAVE,

    /**
     * Incoerência entre campos sem efeito sobre valor declarado, como
     * combinação de códigos que a tabela de referência não admite mas que não
     * altera montante.
     */
    MODERADA,

    /**
     * Observação registrada sem afirmação de erro: dado incomum, campo
     * preenchido de forma redundante, situação que merece leitura humana.
     */
    INFORMATIVA;

    /**
     * Indica se esta severidade é mais grave que a outra.
     *
     * <p>Apoia-se na ordem de declaração das constantes, da mais grave para a
     * menos grave.</p>
     */
    public boolean maisGraveQue(Severidade outra) {
        return this.ordinal() < outra.ordinal();
    }
}
