package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/**
 * Os apontamentos de uma execução, recortados e filtrados.
 *
 * <p>A ordem é a do banco — da severidade mais grave para a menos grave, e depois
 * por documento, item e regra —, e é determinística. Paginar sobre ordem instável
 * devolveria a mesma linha em duas páginas e nenhuma vez em outra.</p>
 */
public record RespostaDeAchados(
        String execucaoId,
        PaginaExposta pagina,
        FiltroExposto filtro,
        List<AchadoExposto> achados) {

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
