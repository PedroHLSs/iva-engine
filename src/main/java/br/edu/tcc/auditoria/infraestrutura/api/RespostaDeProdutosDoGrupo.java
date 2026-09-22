package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/**
 * As notas e os itens que compõem um grupo do lote.
 *
 * <p>É o caminho de volta ao concreto: do erro de parametrização para as notas em
 * que ele aparece, e de lá para o detalhe de cada produto. O grupo vem repetido no
 * cabeçalho para que a página diga a que agrupamento ela pertence, sem depender de
 * a tela ter guardado isso da chamada anterior.</p>
 */
public record RespostaDeProdutosDoGrupo(
        String analiseId,
        GrupoExposto grupo,
        PaginaExposta pagina,
        List<ProdutoExposto> produtos,
        FaixaDeNatureza natureza,
        String aviso) {

    public RespostaDeProdutosDoGrupo {
        if (analiseId == null || analiseId.isBlank()) {
            throw new RespostaInvalida("A página precisa dizer de que análise ela é.");
        }
        if (grupo == null) {
            throw new RespostaInvalida(
                    "A página precisa dizer de que grupo ela é: sem isso, ela é uma lista de produtos "
                            + "sem contexto.");
        }
        if (pagina == null) {
            throw new RespostaInvalida("A página precisa da paginação.");
        }
        if (produtos == null) {
            throw new RespostaInvalida(
                    "A lista de produtos deve ser vazia quando a página não alcança nenhum, nunca "
                            + "nula.");
        }
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência. Dado de demonstração "
                            + "sem aviso é afirmação falsa sobre a lei.");
        }
        if (aviso == null || aviso.isBlank()) {
            throw new RespostaInvalida("Toda resposta de resultado sai com o aviso de uso.");
        }
        produtos = List.copyOf(produtos);
    }
}
