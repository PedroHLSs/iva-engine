package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * Os critérios que a resposta de fato aplicou.
 *
 * <p>Devolvido junto do resultado, e com {@code null} onde não houve critério.
 * Sem isso, uma listagem com poucas linhas é ambígua: pode ser um acervo limpo
 * ou um filtro que ninguém notou que estava ligado.</p>
 */
public record FiltroExposto(String regraId, String severidade, String statusDeTratativa) {

    public static FiltroExposto nenhum() {
        return new FiltroExposto(null, null, null);
    }
}
