package br.edu.tcc.auditoria.infraestrutura.upload;

// Representa quanto o sistema aceita receber pela web. Tem valor padrão, ao contrário do sal e da tolerância, porque um limite de tamanho não afirma nada sobre documento nenhum; todos podem ser configurados.
public record LimitesDeUpload(
        long tamanhoMaximoDoEnvio,
        int entradasMaximasNoPacote,
        long tamanhoMaximoPorEntrada,
        long totalDescomprimidoMaximo,
        long razaoDeCompressaoMaxima,
        long pisoParaConferirRazao) {

    private static final long MEGABYTE = 1024L * 1024L;

    // Valida que todos os limites sejam maiores que zero e que o piso da razão não seja negativo.
    public LimitesDeUpload {
        exigirPositivo(tamanhoMaximoDoEnvio, "tamanhoMaximoDoEnvio");
        exigirPositivo(entradasMaximasNoPacote, "entradasMaximasNoPacote");
        exigirPositivo(tamanhoMaximoPorEntrada, "tamanhoMaximoPorEntrada");
        exigirPositivo(totalDescomprimidoMaximo, "totalDescomprimidoMaximo");
        exigirPositivo(razaoDeCompressaoMaxima, "razaoDeCompressaoMaxima");
        if (pisoParaConferirRazao < 0) {
            throw new IllegalArgumentException(
                    "O piso para conferir a razão de compressão não pode ser negativo.");
        }
    }

    // Método estático que devolve os limites padrão. A razão de compressão só é conferida acima de 1 MB, porque XML fiscal comprime muito e razão alta é normal em arquivo pequeno.
    public static LimitesDeUpload padrao() {
        return new LimitesDeUpload(
                64 * MEGABYTE,
                5_000,
                16 * MEGABYTE,
                512 * MEGABYTE,
                500,
                MEGABYTE);
    }

    // Retorna o limite do envio em megabytes, para a mensagem de recusa.
    public String envioEmMegabytes() {
        return emMegabytes(tamanhoMaximoDoEnvio);
    }

    // Retorna o limite por entrada em megabytes, para a mensagem de recusa.
    public String porEntradaEmMegabytes() {
        return emMegabytes(tamanhoMaximoPorEntrada);
    }

    // Retorna o limite total descomprimido em megabytes, para a mensagem de recusa.
    public String totalEmMegabytes() {
        return emMegabytes(totalDescomprimidoMaximo);
    }

    // Método auxiliar que escreve bytes como megabytes.
    private static String emMegabytes(long bytes) {
        return "%d MB".formatted(bytes / MEGABYTE);
    }

    // Método auxiliar que exige valor maior que zero.
    private static void exigirPositivo(long valor, String nomeDoCampo) {
        if (valor < 1) {
            throw new IllegalArgumentException(
                    "O limite \"%s\" deve ser maior que zero, mas veio %d.".formatted(nomeDoCampo, valor));
        }
    }
}
