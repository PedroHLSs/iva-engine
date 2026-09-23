package br.edu.tcc.auditoria.infraestrutura.xml;

// Representa um arquivo do lote que não virou documento: de onde veio, o tipo do erro e o motivo. Nenhum arquivo ruim derruba o lote, e nenhum some sem registro; não tem data e hora, para dois relatórios do mesmo lote poderem ser comparados.
public record FalhaDeLeitura(String origem, String tipoDeErro, String motivo) {

    // Valida que haja origem, tipo de erro e motivo.
    public FalhaDeLeitura {
        if (origem == null || origem.isBlank()) {
            throw new IllegalArgumentException("A falha de leitura precisa dizer de qual arquivo veio.");
        }
        if (tipoDeErro == null || tipoDeErro.isBlank()) {
            throw new IllegalArgumentException("A falha de leitura precisa dizer que tipo de erro ocorreu.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("A falha de leitura precisa dizer por que ocorreu.");
        }
    }

    // Método estático que monta a falha a partir da exceção que parou a leitura do arquivo.
    static FalhaDeLeitura de(String origem, Throwable erro) {
        String motivo = erro.getMessage() == null || erro.getMessage().isBlank()
                ? "A leitura falhou sem mensagem."
                : erro.getMessage();
        return new FalhaDeLeitura(origem, erro.getClass().getSimpleName(), motivo);
    }
}
