package br.edu.tcc.auditoria.aplicacao.conferencia;

/**
 * Quanto da chave de agrupamento o produto tinha para dar.
 *
 * <h2>A chave degrada, e o nível vai escrito</h2>
 *
 * <p>É a mesma disciplina da D010, agora com dado melhor: lá o NCM e o
 * {@code cClassTrib} só existiam dentro das evidências, e só quando a regra os
 * examinava, de modo que o nível dependia de qual regra apontou. Aqui eles vêm de
 * {@code item_documento} — o que o documento declarou —, e por isso o nível
 * depende só do documento.</p>
 *
 * <p>O que <strong>não</strong> mudou é que o nível continua indo escrito no
 * grupo. Dois grupos agrupados por critérios diferentes não são comparáveis, e
 * apresentá-los na mesma lista sem dizer isso seria somar coisas que não se
 * somam.</p>
 *
 * <p>Um produto sem NCM e sem {@code cClassTrib} não é ignorado e não é jogado
 * num grupo "outros" mudo: ele forma grupo próprio, com o nível dizendo o que
 * faltou. É a mesma razão pela qual a Etapa 9 pôs os grupos sem valor calculável
 * em seção própria, e não no rodapé da lista ordenada por dinheiro.</p>
 */
public enum NivelDoAgrupamento {

    /** O documento declarou os dois. */
    NCM_E_CLASSTRIB("agrupado por NCM e cClassTrib, os dois declarados no documento"),

    /** Declarou NCM, não declarou {@code cClassTrib}. */
    SOMENTE_NCM("agrupado só por NCM: o documento não declarou cClassTrib para estes itens"),

    /** Declarou {@code cClassTrib}, não declarou NCM. */
    SOMENTE_CLASSTRIB("agrupado só por cClassTrib: o documento não declarou NCM para estes itens"),

    /** Não declarou nenhum dos dois. */
    SEM_NENHUM_DOS_DOIS(
            "não foi possível agrupar por enquadramento: o documento não declarou NCM nem cClassTrib "
                    + "para estes itens. Eles continuam na lista porque sumir seria pior");

    private final String rotulo;

    NivelDoAgrupamento(String rotulo) {
        this.rotulo = rotulo;
    }

    /** A frase inteira, para a tela não ter de traduzir o código. */
    public String rotulo() {
        return rotulo;
    }

    /** O nível que corresponde ao que o documento declarou. */
    public static NivelDoAgrupamento de(boolean temNcm, boolean temClassTrib) {
        if (temNcm && temClassTrib) {
            return NCM_E_CLASSTRIB;
        }
        if (temNcm) {
            return SOMENTE_NCM;
        }
        if (temClassTrib) {
            return SOMENTE_CLASSTRIB;
        }
        return SEM_NENHUM_DOS_DOIS;
    }
}
