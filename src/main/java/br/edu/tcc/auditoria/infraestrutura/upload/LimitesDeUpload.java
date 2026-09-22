package br.edu.tcc.auditoria.infraestrutura.upload;

/**
 * Quanto o sistema aceita receber pela web.
 *
 * <p><strong>Estes têm valor padrão, ao contrário do sal e da tolerância.</strong>
 * A diferença não é de estilo: aqueles dois são escolhas que decidem o que o
 * relatório afirma — quanta divergência some, e se o pseudônimo é reversível —,
 * e escolher por conta própria seria decidir em nome de quem audita. Um limite de
 * tamanho de arquivo não afirma nada sobre documento nenhum; é dimensionamento
 * de máquina, e deixar o sistema parar na subida por falta dele seria zelo mal
 * colocado.</p>
 *
 * <p>Todos são configuráveis. Quem tiver acervo maior que o padrão aumenta os
 * números e segue.</p>
 */
public record LimitesDeUpload(
        long tamanhoMaximoDoEnvio,
        int entradasMaximasNoPacote,
        long tamanhoMaximoPorEntrada,
        long totalDescomprimidoMaximo,
        long razaoDeCompressaoMaxima,
        long pisoParaConferirRazao) {

    private static final long MEGABYTE = 1024L * 1024L;

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

    /**
     * Os padrões.
     *
     * <p>O piso de 1 MB para a conferência de razão merece explicação, porque sem
     * ele a proteção recusaria dado legítimo: <strong>XML de documento fiscal
     * comprime muito</strong> — é texto com marcação repetida —, e razões de
     * 30:1 ou 50:1 são normais num arquivo honesto. Abaixo do piso, uma razão
     * alta não é ameaça nenhuma: o teto absoluto por entrada já limita o estrago
     * a {@code tamanhoMaximoPorEntrada}, e é ele que faz o trabalho. A razão
     * existe como segunda barreira para a entrada que já é grande depois de
     * descomprimida.</p>
     */
    public static LimitesDeUpload padrao() {
        return new LimitesDeUpload(
                64 * MEGABYTE,
                5_000,
                16 * MEGABYTE,
                512 * MEGABYTE,
                500,
                MEGABYTE);
    }

    /** O tamanho do envio, escrito em megabytes, para a mensagem de recusa. */
    public String envioEmMegabytes() {
        return emMegabytes(tamanhoMaximoDoEnvio);
    }

    public String porEntradaEmMegabytes() {
        return emMegabytes(tamanhoMaximoPorEntrada);
    }

    public String totalEmMegabytes() {
        return emMegabytes(totalDescomprimidoMaximo);
    }

    private static String emMegabytes(long bytes) {
        return "%d MB".formatted(bytes / MEGABYTE);
    }

    private static void exigirPositivo(long valor, String nomeDoCampo) {
        if (valor < 1) {
            throw new IllegalArgumentException(
                    "O limite \"%s\" deve ser maior que zero, mas veio %d.".formatted(nomeDoCampo, valor));
        }
    }
}
