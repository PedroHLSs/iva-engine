package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa uma página das avaliações que a execução não conseguiu concluir. Fica num endereço próprio, e não dentro dos achados, porque costuma ter muito mais linhas.
public record RespostaDeNaoAvaliados(
        String execucaoId,
        PaginaExposta pagina,
        FiltroExposto filtro,
        List<NaoAvaliadaExposta> naoAvaliados) {

    // Valida que a resposta tenha execução, página, filtro e a lista, vazia quando tudo foi avaliado.
    public RespostaDeNaoAvaliados {
        if (execucaoId == null || pagina == null || filtro == null) {
            throw new RespostaInvalida(
                    "A resposta de não avaliados precisa da execução, da paginação e do filtro.");
        }
        if (naoAvaliados == null) {
            throw new RespostaInvalida(
                    "A lista de não avaliados deve ser vazia quando a execução avaliou tudo, nunca "
                            + "nula. Vazia e ausente não são a mesma informação.");
        }
        naoAvaliados = List.copyOf(naoAvaliados);
    }
}
