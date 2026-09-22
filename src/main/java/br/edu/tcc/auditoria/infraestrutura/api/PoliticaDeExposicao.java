package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * O que a API mostra de dado sensível, e o que ela omite dizendo por quê.
 *
 * <h2>Por que os padrões são desligados</h2>
 *
 * <p>Dois campos que a CLI já mostra passam a poder trafegar por HTTP: a chave de
 * acesso, cujos dígitos intermediários são o CNPJ do emitente (D005, D007), e a
 * justificativa da tratativa, texto livre digitado por pessoa e sem sanitização,
 * que pode conter CNPJ ou razão social.</p>
 *
 * <h2>O terceiro campo, acrescentado na etapa de conferência</h2>
 *
 * <p>A descrição do produto entra sob o mesmo regime, e é o <strong>pior</strong>
 * dos três nesse aspecto. A justificativa é escrita por quem audita, uma de cada
 * vez, sabendo que vai ficar registrada. O xProd vem da fonte, em escala, e
 * ninguém o revisou: na prática traz nome de cliente, referência de pedido,
 * número de contrato, "P/ OBRA FULANO". Quem grava já substitui corrida de 44
 * dígitos pelo marcador, mas nenhuma regra de forma alcança prosa.</p>
 *
 * <p>No terminal, o alcance de um {@code listar-achados} é a sessão de quem
 * digitou. Uma resposta HTTP é outra coisa: é copiável, cacheável por
 * intermediário, gravável em log de acesso, e chega a um cliente que ninguém
 * auditou. O trade-off aceito na D008 para saída de terminal não se transporta
 * para transporte de rede, e é por isso que aqui o padrão se inverte.</p>
 *
 * <h2>É configuração, não parâmetro de consulta</h2>
 *
 * <p>Fosse query param, quem consome a API escolheria quanto dado pessoal
 * receber — e a primeira integração escreveria {@code ?chave=true} porque foi
 * conveniente. Quem decide é quem instala.</p>
 *
 * @param chaveDeAcesso      ligado por {@code auditoria.api.expor-chave-de-acesso}
 * @param justificativa      ligado por {@code auditoria.api.expor-justificativa}
 * @param descricaoDoProduto ligado por {@code auditoria.api.expor-descricao-do-produto}
 */
public record PoliticaDeExposicao(
        boolean chaveDeAcesso, boolean justificativa, boolean descricaoDoProduto) {

    static final String CHAVE_OMITIDA =
            "a chave de acesso não é exposta por esta instalação: seus dígitos intermediários "
                    + "carregam o CNPJ do emitente. Ligue \"auditoria.api.expor-chave-de-acesso\" "
                    + "para incluí-la.";

    static final String JUSTIFICATIVA_OMITIDA =
            "a justificativa não é exposta por esta instalação: é texto livre e pode conter CNPJ ou "
                    + "razão social. Ligue \"auditoria.api.expor-justificativa\" para incluí-la.";

    static final String DESCRICAO_OMITIDA =
            "a descrição do produto não é exposta por esta instalação: é texto livre digitado pelo "
                    + "emitente, em escala e sem revisão, e costuma trazer nome de cliente e "
                    + "referência de pedido. Ligue \"auditoria.api.expor-descricao-do-produto\" "
                    + "para incluí-la.";

    /** O padrão da etapa: só o pseudônimo, sem justificativa e sem descrição. */
    public static PoliticaDeExposicao restritiva() {
        return new PoliticaDeExposicao(false, false, false);
    }

    /**
     * A chave, se esta instalação a expõe; {@code null} se não.
     *
     * <p>Devolver {@code null} aqui, e não {@code Optional}, é deliberado: é este
     * o valor que vai para o JSON, e o campo irmão que o explica sai de
     * {@link #motivoDaChaveOmitida()}. Os dois métodos são complementares por
     * construção — um devolve nulo exatamente quando o outro devolve texto.</p>
     */
    public String chaveOuNulo(String chave) {
        return chaveDeAcesso ? chave : null;
    }

    public String motivoDaChaveOmitida() {
        return chaveDeAcesso ? null : CHAVE_OMITIDA;
    }

    public String justificativaOuNulo(String justificativaRegistrada) {
        return justificativa ? justificativaRegistrada : null;
    }

    public String motivoDaJustificativaOmitida() {
        return justificativa ? null : JUSTIFICATIVA_OMITIDA;
    }

    /**
     * A descrição do produto, se esta instalação a expõe.
     *
     * <p>Recebe já o que o acervo gravou: ou o texto, ou o motivo de não haver
     * um. Quando a instalação não expõe, o texto é trocado pelo motivo da
     * política — de modo que a tela nunca recebe um campo vazio sem explicação,
     * qualquer que seja a razão de ele estar vazio.</p>
     */
    public String descricaoOuNulo(String descricaoGravada) {
        return descricaoDoProduto ? descricaoGravada : null;
    }

    public String motivoDaDescricaoOmitida() {
        return descricaoDoProduto ? null : DESCRICAO_OMITIDA;
    }
}
