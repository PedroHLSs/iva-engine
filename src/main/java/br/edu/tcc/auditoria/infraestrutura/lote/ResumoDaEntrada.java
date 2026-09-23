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

// Classe que calcula o resumo SHA-256 dos arquivos de uma origem, que é o hashEntrada da execução: lotes iguais dão o mesmo resumo, e qualquer arquivo a mais, a menos ou alterado muda o resumo. Entram só os .xml, em ordem alfabética, inclusive os que não puderam ser lidos.
final class ResumoDaEntrada {

    private static final String ALGORITMO = "SHA-256";
    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final String EXTENSAO_DE_PACOTE = ".zip";
    private static final byte SEPARADOR = 0x1f;

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private ResumoDaEntrada() {
    }

    // Método estático que calcula o resumo de uma pasta ou de um .zip; recusa outra coisa.
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

    // Método auxiliar que calcula o resumo dos .xml de uma pasta.
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
            // Barras iguais nos dois sistemas, para o mesmo lote dar o mesmo resumo no Windows e no Linux.
            String nome = diretorio.relativize(arquivo).toString().replace('\\', '/');
            try (InputStream conteudo = Files.newInputStream(arquivo)) {
                acrescentar(resumoDoLote, nome, conteudo);
            }
        }
        return emHexadecimalMinusculo(resumoDoLote.digest());
    }

    // Método auxiliar que calcula o resumo dos .xml de um .zip.
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

    // Método auxiliar que acrescenta ao resumo o nome e o resumo do conteúdo de um arquivo.
    private static void acrescentar(MessageDigest resumoDoLote, String nome, InputStream conteudo)
            throws IOException {
        resumoDoLote.update(nome.getBytes(StandardCharsets.UTF_8));
        resumoDoLote.update(SEPARADOR);
        resumoDoLote.update(resumoDe(conteudo));
        resumoDoLote.update(SEPARADOR);
    }

    // Método auxiliar que calcula o resumo do conteúdo de um arquivo, sem guardar nada dele.
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

    // Método auxiliar que cria um calculador de SHA-256.
    private static MessageDigest resumoNovo() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException semAlgoritmo) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.",
                    semAlgoritmo);
        }
    }

    // Método auxiliar que diz se o nome termina em .xml.
    private static boolean ehDocumento(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_DOCUMENTO);
    }

    // Método auxiliar que diz se a origem é um .zip.
    private static boolean ehPacote(Path origem) {
        return origem.getFileName() != null
                && origem.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith(EXTENSAO_DE_PACOTE);
    }

    // Método auxiliar que escreve os bytes em hexadecimal minúsculo.
    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte umByte : bytes) {
            texto.append(Character.forDigit((umByte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(umByte & 0xf, 16));
        }
        return texto.toString();
    }
}
