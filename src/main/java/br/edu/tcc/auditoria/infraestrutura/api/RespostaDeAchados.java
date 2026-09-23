package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa uma página dos apontamentos de uma execução, com o filtro aplicado. A ordem vem do banco, da gravidade maior para a menor, e é sempre a mesma, para a paginação não repetir nem pular linha.
public record RespostaDeAchados(
        String execucaoId,
        PaginaExposta pagina,
        FiltroExposto filtro,
        List<AchadoExposto> achados) {

    // Valida que a resposta tenha execução, página, filtro e a lista de achados, vazia se nenhum atender ao filtro.
    public RespostaDeAchados {
        if (execucaoId == null || pagina == null || filtro == null) {
            throw new RespostaInvalida(
                    "A resposta de achados precisa da execução, da paginação e do filtro aplicado.");
        }
        if (achados == null) {
            throw new RespostaInvalida(
                    "A lista de achados deve ser vazia quando nenhum atende ao filtro, nunca nula.");
        }
        achados = List.copyOf(achados);
    }
}
