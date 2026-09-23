package br.edu.tcc.auditoria.aplicacao.conferencia;

// Enum com os critérios de ordenação dos grupos e o que cada um significa; a ordem padrão mede exposição, não gravidade.
public enum OrdemDosGrupos {

    // Maior valor de produtos envolvidos primeiro.
    VALOR_DOS_PRODUTOS(
            "valor dos produtos envolvidos, do maior para o menor",
            "Esta ordem mede exposição, não gravidade. Um grupo caro com erro simples de "
                    + "preenchimento aparece acima de um grupo barato com benefício perdido. Para a "
                    + "pergunta \"qual erro se repete mais\", ordene por quantidade."),

    // Mais produtos primeiro.
    QUANTIDADE_DE_PRODUTOS(
            "quantidade de produtos, da maior para a menor",
            "Esta ordem mede repetição, não valor nem gravidade. Ela mostra qual parametrização "
                    + "alcança mais itens, que costuma ser a que vale corrigir primeiro no cadastro.");

    private final String rotulo;
    private final String significado;

    // Construtor que associa a cada ordem o rótulo e o significado exibidos na tela.
    OrdemDosGrupos(String rotulo, String significado) {
        this.rotulo = rotulo;
        this.significado = significado;
    }

    public String rotulo() {
        return rotulo;
    }

    public String significado() {
        return significado;
    }

    // Retorna a ordem padrão, usada quando ninguém escolheu.
    public static OrdemDosGrupos padrao() {
        return VALOR_DOS_PRODUTOS;
    }
}
