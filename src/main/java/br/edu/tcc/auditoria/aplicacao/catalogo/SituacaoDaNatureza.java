package br.edu.tcc.auditoria.aplicacao.catalogo;

/**
 * O que a tela precisa dizer sobre a procedência do que está exibindo.
 *
 * <h2>Quatro estados, e o quarto é o que o projeto quase não teve</h2>
 *
 * <p>{@link #PARCIALMENTE_FICTICIO} é a razão de a natureza ser guardada por
 * tabela. Um sinalizador de carga inteira teria de escolher entre chamar a carga
 * de real ou de fictícia quando ela é as duas coisas, e qualquer das escolhas
 * mentiria sobre metade da tela.</p>
 *
 * <p>{@link #NAO_DECLARADA} não é sinônimo de {@link #NORMATIVO}. Carga gravada
 * antes desta etapa não disse de onde veio, e supor que é norma vigente seria a
 * afirmação mais cara que o sistema poderia fazer por engano — é a mesma
 * disciplina da D002 aplicada à procedência do catálogo.</p>
 */
public enum SituacaoDaNatureza {

    /** Todas as tabelas com registro são de demonstração. */
    INTEIRAMENTE_FICTICIO(
            "Dados de demonstração",
            "Todas as tabelas desta carga foram declaradas como fictícias. Nada nesta tela pode ser "
                    + "lido como afirmação sobre a legislação."),

    /** Parte das tabelas é de demonstração, parte não. */
    PARCIALMENTE_FICTICIO(
            "Catálogo parcialmente fictício",
            "Parte das tabelas desta carga foi declarada como fictícia, e parte como normativa. As "
                    + "tabelas fictícias vêm listadas: o que sai delas é demonstração."),

    /** Nenhuma tabela é de demonstração. */
    NORMATIVO(
            "Catálogo normativo",
            "Todas as tabelas desta carga foram declaradas como transcritas de fonte normativa por "
                    + "quem a montou. O sistema repete o que foi importado; ele não confere a "
                    + "transcrição."),

    /** A carga é anterior à declaração de natureza. */
    NAO_DECLARADA(
            "Procedência não declarada",
            "Esta carga foi importada antes de o sistema passar a exigir a declaração de natureza, e "
                    + "não há como saber se o conteúdo dela é normativo ou de demonstração. Não se "
                    + "supõe que seja normativo: reimporte o catálogo para que a procedência fique "
                    + "registrada.");

    private final String rotulo;
    private final String explicacao;

    SituacaoDaNatureza(String rotulo, String explicacao) {
        this.rotulo = rotulo;
        this.explicacao = explicacao;
    }

    /** A frase curta, para a faixa. */
    public String rotulo() {
        return rotulo;
    }

    /** A frase inteira, para quem quiser saber o que a faixa quer dizer. */
    public String explicacao() {
        return explicacao;
    }

    /** Se a tela precisa avisar que há conteúdo de demonstração à vista. */
    public boolean exigeAviso() {
        return this != NORMATIVO;
    }
}
