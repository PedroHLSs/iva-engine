package br.edu.tcc.auditoria.infraestrutura.xml;

/**
 * Registro de um arquivo do lote que não pôde ser transformado em documento.
 *
 * <p>Um lote de documentos fiscais reais tem arquivo truncado, arquivo que não é
 * NF-e, arquivo com campo fora da forma esperada. Nenhum deles pode derrubar o
 * lote inteiro, e nenhum deles pode sumir: lote de mil arquivos que produz
 * novecentos documentos sem dizer o que houve com os outros cem é relatório que
 * mente por omissão.</p>
 *
 * <p>{@code origem} identifica o arquivo dentro do lote — caminho no disco ou
 * nome da entrada dentro do ZIP. {@code tipoDeErro} é o nome simples da exceção,
 * que separa "XML malformado" de "chave de acesso com tamanho errado" sem
 * obrigar quem lê o relatório a interpretar a mensagem.</p>
 *
 * <p>Não há carimbo de tempo aqui de propósito: dois relatórios do mesmo lote
 * precisam poder ser comparados linha a linha.</p>
 */
public record FalhaDeLeitura(String origem, String tipoDeErro, String motivo) {

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

    /** Monta a falha a partir da exceção que interrompeu a leitura do arquivo. */
    static FalhaDeLeitura de(String origem, Throwable erro) {
        String motivo = erro.getMessage() == null || erro.getMessage().isBlank()
                ? "A leitura falhou sem mensagem."
                : erro.getMessage();
        return new FalhaDeLeitura(origem, erro.getClass().getSimpleName(), motivo);
    }
}
