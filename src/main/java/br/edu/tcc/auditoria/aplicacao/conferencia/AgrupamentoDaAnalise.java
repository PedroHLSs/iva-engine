package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Representa os produtos de uma análise reunidos em grupos por enquadramento declarado e situação, para a tela do lote.
public record AgrupamentoDaAnalise(
        OrdemDosGrupos ordem, ResumoDaConferencia resumo, List<GrupoDeProdutos> grupos) {

    // Valida o agrupamento e confere que a soma dos grupos é igual ao total de produtos da análise.
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

    // Método estático que agrupa os produtos por chave e ordena os grupos pelo critério escolhido, desempatando pela chave.
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

    // Retorna o grupo com a chave indicada, se ele existir neste agrupamento.
    public java.util.Optional<GrupoDeProdutos> grupo(ChaveDoGrupo chave) {
        if (chave == null) {
            throw new ConferenciaInvalida("Não há chave de grupo a procurar.");
        }
        return grupos.stream().filter(grupo -> grupo.chave().equals(chave)).findFirst();
    }

    // Retorna a soma do valor dos produtos de todos os grupos, que não é valor em risco.
    public BigDecimal valorDosProdutos() {
        return grupos.stream()
                .map(GrupoDeProdutos::valorDosProdutos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Método auxiliar que monta o comparador do critério escolhido, com a chave como desempate estável.
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
