package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Os produtos de uma análise, reunidos por enquadramento declarado e situação.
 *
 * <h2>A tela do lote é outra tela</h2>
 *
 * <p>Não é a tela da nota repetida n vezes. A pergunta muda: na nota, a pergunta
 * é "o que tem neste documento"; no lote, é "o que precisa ser corrigido no
 * cadastro". A resposta certa para a segunda é um grupo por parametrização, com
 * quantas vezes ela aparece e quanto valor ela alcança.</p>
 *
 * <h2>O total dos grupos volta a ser o total dos produtos</h2>
 *
 * <p>Todo produto entra em exatamente um grupo, inclusive os que não declararam
 * NCM nem {@code cClassTrib} — que formam grupo próprio com o nível dizendo isso.
 * O construtor confere a soma, porque um agrupamento que perde linhas pelo
 * caminho apresenta um lote menor do que ele é, e ninguém percebe.</p>
 */
public record AgrupamentoDaAnalise(
        OrdemDosGrupos ordem, ResumoDaConferencia resumo, List<GrupoDeProdutos> grupos) {

    public AgrupamentoDaAnalise {
        if (ordem == null) {
            throw new ConferenciaInvalida(
                    "O agrupamento precisa dizer por que critério ele está ordenado, e o que o critério "
                            + "significa.");
        }
        if (resumo == null) {
            throw new ConferenciaInvalida("O agrupamento precisa do resumo do lote inteiro.");
        }
        if (grupos == null) {
            throw new ConferenciaInvalida(
                    "A lista de grupos deve ser vazia quando a análise não leu produto nenhum, nunca "
                            + "nula.");
        }
        if (grupos.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A lista de grupos não pode conter elemento nulo.");
        }
        int somados = grupos.stream().mapToInt(GrupoDeProdutos::quantidadeDeProdutos).sum();
        if (somados != resumo.quantidadeDeProdutos()) {
            throw new ConferenciaInvalida(
                    ("Os grupos somam %d produto(s) e a análise tem %d. Produto que não cai em grupo "
                            + "nenhum some da tela do lote, e some sem deixar rastro.")
                            .formatted(somados, resumo.quantidadeDeProdutos()));
        }
        grupos = List.copyOf(grupos);
    }

    /**
     * Agrupa e ordena.
     *
     * <p>A ordenação é total e determinística: o critério escolhido, depois a
     * chave, para que dois grupos empatados não troquem de lugar entre duas
     * aberturas da mesma tela.</p>
     */
    public static AgrupamentoDaAnalise de(
            List<ProdutoConferido> produtos, OrdemDosGrupos ordem) {

        if (produtos == null) {
            throw new ConferenciaInvalida("Não há produtos a agrupar.");
        }
        if (ordem == null) {
            throw new ConferenciaInvalida("Não há ordem pela qual agrupar.");
        }

        Map<ChaveDoGrupo, List<ProdutoConferido>> porChave = new LinkedHashMap<>();
        for (ProdutoConferido produto : produtos) {
            porChave.computeIfAbsent(ChaveDoGrupo.de(produto), chave -> new ArrayList<>())
                    .add(produto);
        }

        List<GrupoDeProdutos> grupos = new ArrayList<>();
        porChave.forEach((chave, doGrupo) -> grupos.add(GrupoDeProdutos.de(chave, doGrupo)));
        grupos.sort(comparadorDe(ordem));

        return new AgrupamentoDaAnalise(
                ordem,
                ResumoDaConferencia.de(produtos.stream().map(ProdutoConferido::situacao).toList()),
                grupos);
    }

    /** O grupo de chave indicada, se ele existe neste agrupamento. */
    public java.util.Optional<GrupoDeProdutos> grupo(ChaveDoGrupo chave) {
        if (chave == null) {
            throw new ConferenciaInvalida("Não há chave de grupo a procurar.");
        }
        return grupos.stream().filter(grupo -> grupo.chave().equals(chave)).findFirst();
    }

    /** A soma dos valores de todos os grupos — ver {@link GrupoDeProdutos#ROTULO_DO_VALOR}. */
    public BigDecimal valorDosProdutos() {
        return grupos.stream()
                .map(GrupoDeProdutos::valorDosProdutos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Comparator<GrupoDeProdutos> comparadorDe(OrdemDosGrupos ordem) {
        Comparator<GrupoDeProdutos> escolhido = switch (ordem) {
            case VALOR_DOS_PRODUTOS -> Comparator
                    .comparing(GrupoDeProdutos::valorDosProdutos)
                    .reversed();
            case QUANTIDADE_DE_PRODUTOS -> Comparator
                    .comparingInt(GrupoDeProdutos::quantidadeDeProdutos)
                    .reversed();
        };
        return escolhido.thenComparing(GrupoDeProdutos::chave, ChaveDoGrupo.ORDEM_ESTAVEL);
    }
}
