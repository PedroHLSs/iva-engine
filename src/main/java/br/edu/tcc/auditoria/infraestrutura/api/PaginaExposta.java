package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * O recorte que a resposta traz do conjunto inteiro.
 *
 * <p>Vai na resposta mesmo quando tudo cabe numa página. Quem consome precisa
 * saber que viu tudo, e "vi vinte linhas" não responde isso sem o total ao
 * lado — um relatório de auditoria truncado em silêncio é pior que um relatório
 * grande.</p>
 */
public record PaginaExposta(int numero, int tamanho, long totalDeElementos, int totalDePaginas) {

    /** Tamanho máximo aceito, para que um pedido não carregue o acervo inteiro. */
    public static final int TAMANHO_MAXIMO = 500;

    public PaginaExposta {
        if (numero < 0) {
            throw new RespostaInvalida("O número da página não pode ser negativo, mas veio %d."
                    .formatted(numero));
        }
        if (tamanho < 1) {
            throw new RespostaInvalida("O tamanho da página deve ser pelo menos 1, mas veio %d."
                    .formatted(tamanho));
        }
        if (totalDeElementos < 0 || totalDePaginas < 0) {
            throw new RespostaInvalida("A contagem da paginação não pode ser negativa.");
        }
    }

    public static PaginaExposta de(int numero, int tamanho, long totalDeElementos) {
        int totalDePaginas = (int) ((totalDeElementos + tamanho - 1) / tamanho);
        return new PaginaExposta(numero, tamanho, totalDeElementos, totalDePaginas);
    }
}
