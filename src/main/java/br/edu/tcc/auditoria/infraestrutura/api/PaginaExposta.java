package br.edu.tcc.auditoria.infraestrutura.api;

// Representa a página que a resposta traz, com o total de linhas e de páginas. Vai mesmo quando tudo cabe numa página, para quem lê saber que viu tudo.
public record PaginaExposta(int numero, int tamanho, long totalDeElementos, int totalDePaginas) {

    // Tamanho máximo de página, para um pedido não carregar o acervo inteiro.
    public static final int TAMANHO_MAXIMO = 500;

    // Valida que o número da página não seja negativo, que o tamanho seja pelo menos 1 e que os totais não sejam negativos.
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

    // Método estático que cria a página e calcula quantas páginas existem.
    public static PaginaExposta de(int numero, int tamanho, long totalDeElementos) {
        int totalDePaginas = (int) ((totalDeElementos + tamanho - 1) / tamanho);
        return new PaginaExposta(numero, tamanho, totalDeElementos, totalDePaginas);
    }
}
