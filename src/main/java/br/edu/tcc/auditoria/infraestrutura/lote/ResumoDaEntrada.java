package br.edu.tcc.auditoria.infraestrutura.lote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resumo criptográfico do conjunto de arquivos de uma origem.
 *
 * <p>É o {@code hashEntrada} da execução de auditoria: dois lotes iguais
 * produzem o mesmo resumo, e qualquer diferença — arquivo a mais, arquivo a
 * menos, conteúdo alterado, nome trocado — produz resumo diferente. Sem isso,
 * uma execução gravada não conseguiria dizer <em>sobre o quê</em> ela foi.</p>
 *
 * <h2>O que entra no cálculo</h2>
 *
 * <p>Para cada arquivo XML da origem, o nome relativo e o resumo do conteúdo,
 * na ordem alfabética do nome. A ordem fixa é o que torna o resultado
 * independente da ordem em que o sistema de arquivos devolve as entradas.</p>
 *
 * <p>Os mesmos critérios de seleção do leitor de lote valem aqui: só arquivos
 * com extensão {@code .xml}, dentro de diretório ou de pacote {@code .zip}. Se
 * os dois divergissem, o resumo passaria a descrever um conjunto diferente do
 * que foi efetivamente auditado.</p>
 *
 * <p>Arquivo ilegível não é ignorado no resumo: ele fez parte da entrada, mesmo
 * sem ter virado documento. O resumo descreve o que foi apresentado ao sistema,
 * não o que ele conseguiu ler.</p>
 */
final class ResumoDaEntrada {

    private static final String ALGORITMO = "SHA-256";
    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final String EXTENSAO_DE_PACOTE = ".zip";
    private static final byte SEPARADOR = 0x1f;

    private ResumoDaEntrada() {
    }

    /** Calcula o resumo do conjunto de arquivos da origem. */
    static String de(Path origem) throws IOException {
        if (origem == null) {
            throw new IllegalArgumentException("Não há origem cujo resumo calcular.");
        }
        if (Files.isDirectory(origem)) {
            return resumirDiretorio(origem);
        }
        if (ehPacote(origem)) {
            return resumirPacote(origem);
        }
        throw new IllegalArgumentException(
                ("A origem do lote precisa ser um diretório ou um arquivo \"%s\", e \"%s\" não é nenhum "
                        + "dos dois.").formatted(EXTENSAO_DE_PACOTE, origem));
    }

    private static String resumirDiretorio(Path diretorio) throws IOException {
        List<Path> arquivos;
        try (Stream<Path> percurso = Files.walk(diretorio)) {
            arquivos = percurso
                    .filter(Files::isRegularFile)
                    .filter(caminho -> ehDocumento(caminho.getFileName().toString()))
                    .sorted()
                    .toList();
        }

        MessageDigest resumoDoLote = resumoNovo();
        for (Path arquivo : arquivos) {
            // Separador de barras normalizado: o mesmo lote lido em Windows e em
            // Linux precisa produzir o mesmo resumo.
            String nome = diretorio.relativize(arquivo).toString().replace('\\', '/');
            try (InputStream conteudo = Files.newInputStream(arquivo)) {
                acrescentar(resumoDoLote, nome, conteudo);
            }
        }
        return emHexadecimalMinusculo(resumoDoLote.digest());
    }

    private static String resumirPacote(Path pacote) throws IOException {
        MessageDigest resumoDoLote = resumoNovo();
        try (ZipFile arquivoCompactado = new ZipFile(pacote.toFile(), StandardCharsets.UTF_8)) {
            List<ZipEntry> entradas = new ArrayList<>(arquivoCompactado.stream()
                    .filter(entrada -> !entrada.isDirectory())
                    .filter(entrada -> ehDocumento(entrada.getName()))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList());

            for (ZipEntry entrada : entradas) {
                try (InputStream conteudo = arquivoCompactado.getInputStream(entrada)) {
                    acrescentar(resumoDoLote, entrada.getName(), conteudo);
                }
            }
        }
        return emHexadecimalMinusculo(resumoDoLote.digest());
    }

    private static void acrescentar(MessageDigest resumoDoLote, String nome, InputStream conteudo)
            throws IOException {
        resumoDoLote.update(nome.getBytes(StandardCharsets.UTF_8));
        resumoDoLote.update(SEPARADOR);
        resumoDoLote.update(resumoDe(conteudo));
        resumoDoLote.update(SEPARADOR);
    }

    private static byte[] resumoDe(InputStream conteudo) throws IOException {
        MessageDigest resumoDoArquivo = resumoNovo();
        byte[] descarte = new byte[8192];
        try (DigestInputStream leitura = new DigestInputStream(conteudo, resumoDoArquivo)) {
            while (leitura.read(descarte) != -1) {
                // O conteúdo só interessa ao resumo; nada dele é guardado.
            }
        }
        return resumoDoArquivo.digest();
    }

    private static MessageDigest resumoNovo() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException semAlgoritmo) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.",
                    semAlgoritmo);
        }
    }

    private static boolean ehDocumento(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_DOCUMENTO);
    }

    private static boolean ehPacote(Path origem) {
        return origem.getFileName() != null
                && origem.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith(EXTENSAO_DE_PACOTE);
    }

    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte umByte : bytes) {
            texto.append(Character.forDigit((umByte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(umByte & 0xf, 16));
        }
        return texto.toString();
    }
}
