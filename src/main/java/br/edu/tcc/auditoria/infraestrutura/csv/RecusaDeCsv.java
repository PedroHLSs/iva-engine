package br.edu.tcc.auditoria.infraestrutura.csv;

// Interface que quem chama o leitor de CSV usa para dizer como nomear a recusa, porque só ele sabe se o arquivo é de catálogo ou de gabarito.
@FunctionalInterface
public interface RecusaDeCsv {

    // Monta a exceção que recusa o arquivo, com a causa original quando houver.
    RuntimeException de(String mensagem, Throwable causa);

    // Monta a exceção que recusa o arquivo, quando não há causa.
    default RuntimeException de(String mensagem) {
        return de(mensagem, null);
    }
}
