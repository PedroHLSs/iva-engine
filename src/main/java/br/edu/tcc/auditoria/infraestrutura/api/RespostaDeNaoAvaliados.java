package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa uma página das avaliações que a execução não conseguiu concluir. Fica num endereço próprio, e não dentro dos achados, porque costuma ter muito mais linhas.
public record RespostaDeNaoAvaliados(
        String execucaoId,
        PaginaExposta pagina,
        FiltroExposto filtro,
        List<NaoAvaliadaExposta> naoAvaliados,
        FaixaDeNatureza natureza,
        ToleranciaExposta toleranciaDeValor) {

    // Emenda de 04/10/2026 (D021): traz a faixa de procedência do catálogo da execução.
    // Emenda de 04/10/2026 (D023): traz a tolerância de valor da R05 que a execução usou, com a origem.

    // Valida que a resposta tenha execução, página, filtro e a lista, vazia quando tudo foi avaliado.
    public RespostaDeNaoAvaliados {
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência do catálogo (D021). Dado de "
                            + "demonstração sem aviso é afirmação falsa sobre a lei.");
        }
        if (execucaoId == null || pagina == null || filtro == null) {
            throw new RespostaInvalida(
                    "A resposta de não avaliados precisa da execução, da paginação e do filtro.");
        }
        if (naoAvaliados == null) {
            throw new RespostaInvalida(
                    "A lista de não avaliados deve ser vazia quando a execução avaliou tudo, nunca "
                            + "nula. Vazia e ausente não são a mesma informação.");
        }
        if (toleranciaDeValor == null) {
            throw new RespostaInvalida(
                    "Toda resposta com resultado da R05 sai com a tolerância de valor usada, ou com o motivo "
                            + "de ela não ter sido registrada (D023).");
        }
        naoAvaliados = List.copyOf(naoAvaliados);
    }
}
