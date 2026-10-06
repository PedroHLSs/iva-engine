package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa uma página dos apontamentos de uma execução, com o filtro aplicado. A ordem vem do banco, da gravidade maior para a menor, e é sempre a mesma, para a paginação não repetir nem pular linha.
public record RespostaDeAchados(
        String execucaoId,
        PaginaExposta pagina,
        FiltroExposto filtro,
        List<AchadoExposto> achados,
        FaixaDeNatureza natureza,
        ToleranciaExposta toleranciaDeValor) {

    // Emenda de 04/10/2026 (D021): traz a faixa de procedência do catálogo da execução, porque cada apontamento cita a fonte do catálogo como fundamento.
    // Emenda de 04/10/2026 (D023): traz a tolerância de valor da R05 que a execução usou, com a origem.

    // Valida que a resposta tenha execução, página, filtro e a lista de achados, vazia se nenhum atender ao filtro.
    public RespostaDeAchados {
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência do catálogo (D021). Dado de "
                            + "demonstração sem aviso é afirmação falsa sobre a lei.");
        }
        if (execucaoId == null || pagina == null || filtro == null) {
            throw new RespostaInvalida(
                    "A resposta de achados precisa da execução, da paginação e do filtro aplicado.");
        }
        if (achados == null) {
            throw new RespostaInvalida(
                    "A lista de achados deve ser vazia quando nenhum atende ao filtro, nunca nula.");
        }
        if (toleranciaDeValor == null) {
            throw new RespostaInvalida(
                    "Toda resposta com resultado da R05 sai com a tolerância de valor usada, ou com o motivo "
                            + "de ela não ter sido registrada (D023).");
        }
        achados = List.copyOf(achados);
    }
}
