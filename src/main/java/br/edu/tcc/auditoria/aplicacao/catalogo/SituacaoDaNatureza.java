package br.edu.tcc.auditoria.aplicacao.catalogo;

// Enum que representa o que a tela precisa dizer sobre a procedência da carga, com o rótulo e a explicação de cada situação.
public enum SituacaoDaNatureza {

    // Todas as tabelas com registro são de demonstração.
    INTEIRAMENTE_FICTICIO(
            "Dados de demonstração",
            "Todas as tabelas desta carga foram declaradas como fictícias. Nada nesta tela pode ser "
                    + "lido como afirmação sobre a legislação."),

    // Parte das tabelas é de demonstração, parte não.
    PARCIALMENTE_FICTICIO(
            "Catálogo parcialmente fictício",
            "Parte das tabelas desta carga foi declarada como fictícia, e parte como normativa. As "
                    + "tabelas fictícias vêm listadas: o que sai delas é demonstração."),

    // Nenhuma tabela é de demonstração.
    NORMATIVO(
            "Catálogo normativo",
            "Todas as tabelas desta carga foram declaradas como transcritas de fonte normativa por "
                    + "quem a montou. O sistema repete o que foi importado; ele não confere a "
                    + "transcrição."),

    // A carga é anterior à declaração de natureza, e não se supõe que ela seja normativa.
    NAO_DECLARADA(
            "Procedência não declarada",
            "Esta carga foi importada antes de o sistema passar a exigir a declaração de natureza, e "
                    + "não há como saber se o conteúdo dela é normativo ou de demonstração. Não se "
                    + "supõe que seja normativo: reimporte o catálogo para que a procedência fique "
                    + "registrada.");

    private final String rotulo;
    private final String explicacao;

    // Construtor associa cada situacao da natureza ao rótulo e à explicação que a tela precisa exibir
    SituacaoDaNatureza(String rotulo, String explicacao) {
        this.rotulo = rotulo;
        this.explicacao = explicacao;
    }

    public String rotulo() {
        return rotulo;
    }

    public String explicacao() {
        return explicacao;
    }

    // Indica se a tela precisa exibir aviso, o que vale para toda situação diferente de NORMATIVO.
    public boolean exigeAviso() {
        return this != NORMATIVO;
    }
}
