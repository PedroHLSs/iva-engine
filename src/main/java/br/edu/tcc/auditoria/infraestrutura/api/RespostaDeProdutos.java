package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/** Uma página dos produtos de uma análise. */
public record RespostaDeProdutos(
        String analiseId,
        PaginaExposta pagina,
        List<ProdutoExposto> produtos,
        FaixaDeNatureza natureza,
        String aviso) {

    public RespostaDeProdutos {
        if (analiseId == null || analiseId.isBlank()) {
            throw new RespostaInvalida("A página de produtos precisa dizer de que análise ela é.");
        }
        if (pagina == null) {
            throw new RespostaInvalida("A página de produtos precisa da paginação.");
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
