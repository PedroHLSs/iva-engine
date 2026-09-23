package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa uma página das notas e itens de um grupo do lote. O grupo vem repetido no começo, para a página dizer de que grupo ela é.
public record RespostaDeProdutosDoGrupo(
        String analiseId,
        GrupoExposto grupo,
        PaginaExposta pagina,
        List<ProdutoExposto> produtos,
        FaixaDeNatureza natureza,
        String aviso) {

    // Valida que a página tenha análise, grupo, paginação, lista de produtos, faixa de procedência e aviso de uso.
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
