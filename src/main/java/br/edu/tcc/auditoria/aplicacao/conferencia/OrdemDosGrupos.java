package br.edu.tcc.auditoria.aplicacao.conferencia;

/**
 * Por que critério os grupos vêm ordenados — e o que esse critério significa.
 *
 * <h2>A ordem padrão ordena por exposição, não por gravidade</h2>
 *
 * <p>É preciso dizer isso em voz alta, porque a leitura natural de uma lista
 * ordenada é "o de cima é o mais grave". Não é. Um grupo caro com erro trivial de
 * preenchimento sobe acima de um grupo barato que perdeu um benefício. As duas
 * coisas importam, e são diferentes.</p>
 *
 * <p>Ordenar por valor continua sendo o padrão certo: três ocorrências de valor
 * alto pesam mais que oitocentas irrelevantes, e quem confere precisa começar por
 * onde há mais dinheiro envolvido. Mas quem lê precisa saber que foi isso que a
 * ordenação fez — e por isso existe a segunda ordem, por contagem, para a
 * pergunta oposta: qual erro de cadastro se repete mais.</p>
 *
 * <p>O {@code significado} viaja até a tela. Ele não é ajuda de rodapé: é o que
 * impede a lista de ser lida como ranking de gravidade.</p>
 */
public enum OrdemDosGrupos {

    /** Maior valor de produtos envolvidos primeiro. */
    VALOR_DOS_PRODUTOS(
            "valor dos produtos envolvidos, do maior para o menor",
            "Esta ordem mede exposição, não gravidade. Um grupo caro com erro simples de "
                    + "preenchimento aparece acima de um grupo barato com benefício perdido. Para a "
                    + "pergunta \"qual erro se repete mais\", ordene por quantidade."),

    /** Mais produtos primeiro. */
    QUANTIDADE_DE_PRODUTOS(
            "quantidade de produtos, da maior para a menor",
            "Esta ordem mede repetição, não valor nem gravidade. Ela mostra qual parametrização "
                    + "alcança mais itens, que costuma ser a que vale corrigir primeiro no cadastro.");

    private final String rotulo;
    private final String significado;

    OrdemDosGrupos(String rotulo, String significado) {
        this.rotulo = rotulo;
        this.significado = significado;
    }

    /** Como a ordem se chama na tela. */
    public String rotulo() {
        return rotulo;
    }

    /** O que a ordem quer dizer, escrito para quem lê a lista. */
    public String significado() {
        return significado;
    }

    /** A ordem padrão, quando ninguém escolheu. */
    public static OrdemDosGrupos padrao() {
        return VALOR_DOS_PRODUTOS;
    }
}
