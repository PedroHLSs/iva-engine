package br.edu.tcc.auditoria.infraestrutura.csv;

// Interface para quem chama o leitor de CSV querer saber, além da mensagem, a linha, a coluna e o valor recusados. Acrescentada na Etapa 12 para a importação de catálogo listar todas as linhas recusadas de uma vez; quem só implementa RecusaDeCsv continua recebendo a mesma mensagem de antes.
public interface RecusaPorCampo extends RecusaDeCsv {

    // Monta a exceção que recusa o valor de uma coluna numa linha.
    RuntimeException deCampo(int linha, String coluna, String valor, String mensagem, Throwable causa);

    // Monta a exceção que recusa o arquivo por faltar uma coluna no cabeçalho.
    RuntimeException deColunaAusente(int linha, String coluna, String mensagem);
}
