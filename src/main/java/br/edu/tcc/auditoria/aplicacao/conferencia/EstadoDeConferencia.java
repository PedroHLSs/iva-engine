package br.edu.tcc.auditoria.aplicacao.conferencia;

/**
 * O que a interface diz a quem confere o enquadramento de IBS/CBS de um produto.
 *
 * <p>São quatro, e são vocabulário de auditoria — não afirmação sobre a
 * legislação. Nenhum deles depende de alíquota, código ou artigo: todos saem do
 * que o motor já produziu, pela tabela de {@link TraducaoDeDesfecho}.</p>
 *
 * <h2>A ordem de declaração é a precedência, e isso é carregado</h2>
 *
 * <p>As constantes estão declaradas da mais forte para a mais fraca, como
 * {@code Severidade} faz. {@link SituacaoDoProduto} agrega as verificações de um
 * produto tomando a de menor ordinal, de modo que a precedência não é uma
 * cadeia de {@code if} que alguém pode reordenar sem perceber: ela é a ordem em
 * que estes quatro nomes aparecem aqui.</p>
 *
 * <p>A consequência que mais importa cai fora de graça:
 * {@link #SEM_DIVERGENCIA_IDENTIFICADA} é o último, então um produto só o
 * alcança quando <em>todas</em> as suas verificações o alcançaram. Uma única
 * avaliação não concluída basta para impedir.</p>
 */
public enum EstadoDeConferencia {

    /**
     * O declarado no XML não corresponde ao tratamento que a regra indica.
     *
     * <p>Vem de apontamento com severidade crítica, grave ou moderada.</p>
     */
    POSSIVEL_DIVERGENCIA(
            "Possível divergência",
            "O declarado no documento não corresponde ao que a regra aponta a partir da base "
                    + "normativa carregada."),

    /**
     * Situação que merece análise do responsável fiscal.
     *
     * <p>Vem de apontamento com severidade informativa. Hoje é o R04, e o
     * Javadoc dele diz por que não é divergência: pode haver razão legítima para
     * o contribuinte não ter aproveitado o tratamento, e afirmar erro ali seria
     * ultrapassar o que a ferramenta se propõe a fazer.</p>
     */
    REQUER_CONFERENCIA(
            "Requer conferência",
            "A situação merece leitura de quem responde pelo fiscal. O sistema aponta o que "
                    + "encontrou e não conclui por você."),

    /**
     * Dados insuficientes no documento ou na base normativa carregada.
     *
     * <p>Corresponde a {@code NAO_AVALIADO}, e tem o mesmo peso visual dos
     * outros três. Não é uma ausência de resultado: é um resultado, e o motivo
     * escrito pela regra o acompanha.</p>
     */
    NAO_FOI_POSSIVEL_CONCLUIR(
            "Não foi possível concluir",
            "Faltou dado no documento ou na base normativa carregada, e sem ele a verificação "
                    + "não pôde ser feita."),

    /**
     * Nenhuma violação das regras cadastradas, sobre os campos que elas alcançam.
     *
     * <p><strong>Não escreva "Conferido" aqui, nem em tela nenhuma.</strong> O
     * sistema não conferiu o produto: ele aplicou as regras que tem, sobre os
     * campos que elas leem, e não encontrou violação. "Conferido" afirmaria uma
     * verificação que não aconteceu — que é exatamente o defeito que o R04 foi
     * escrito para não cometer, quando recusou dizer que um tratamento não
     * aproveitado é erro.</p>
     */
    SEM_DIVERGENCIA_IDENTIFICADA(
            "Sem divergência identificada",
            "As regras cadastradas foram aplicadas a este produto, sobre os campos que elas "
                    + "alcançam, e nenhuma encontrou violação.");

    private final String rotulo;
    private final String explicacao;

    EstadoDeConferencia(String rotulo, String explicacao) {
        this.rotulo = rotulo;
        this.explicacao = explicacao;
    }

    /** Como o estado é escrito na tela, sempre junto do código e da cor. */
    public String rotulo() {
        return rotulo;
    }

    /**
     * O que o estado quer dizer, em uma frase.
     *
     * <p>Viaja com o estado de propósito: um rótulo de quatro palavras sozinho
     * numa resposta JSON convida quem consome a inventar o significado.</p>
     */
    public String explicacao() {
        return explicacao;
    }
}
