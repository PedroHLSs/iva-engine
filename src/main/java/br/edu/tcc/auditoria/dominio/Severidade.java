package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.SeveridadeInvalida;

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
     *
     * <p>Ainda não é consumido por nenhuma regra nem pela montagem do papel de
     * trabalho, que hoje ordena por outros critérios. Existe para que a
     * ordenação por gravidade não seja reescrita à mão em cada ponto que
     * precisar dela.</p>
     *
     * @throws SeveridadeInvalida se {@code outra} for nula
     */
    public boolean maisGraveQue(Severidade outra) {
        if (outra == null) {
            throw new SeveridadeInvalida(
                    "A severidade comparada não pode ser nula: não há gravidade a confrontar.");
        }
        return this.ordinal() < outra.ordinal();
    }
}
