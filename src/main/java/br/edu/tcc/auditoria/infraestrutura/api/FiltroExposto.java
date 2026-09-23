package br.edu.tcc.auditoria.infraestrutura.api;

// Representa os filtros que a resposta aplicou, com null onde não houve filtro. Sem isso, uma lista curta poderia parecer acervo limpo quando é só um filtro ligado.
public record FiltroExposto(String regraId, String severidade, String statusDeTratativa) {

    // Método estático que cria o filtro vazio, para quando nada foi filtrado.
    public static FiltroExposto nenhum() {
        return new FiltroExposto(null, null, null);
    }
}
