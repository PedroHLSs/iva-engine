package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/**
 * As avaliações que uma execução não conseguiu concluir.
 *
 * <p>Endpoint próprio, e não um campo dentro da resposta de achados, porque a
 * quantidade é de outra ordem de grandeza: um lote com catálogo incompleto produz
 * muito mais não avaliadas do que apontamentos, e a tabela que as guarda não é
 * deduplicada (D007). Recorte e filtro por regra existem por isso.</p>
 */
public record RespostaDeNaoAvaliados(
        String execucaoId,
        PaginaExposta pagina,
        FiltroExposto filtro,
        List<NaoAvaliadaExposta> naoAvaliados) {

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
