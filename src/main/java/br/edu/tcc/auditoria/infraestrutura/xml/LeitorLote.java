package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Classe que lê um lote de documentos, de uma pasta ou de um .zip, e devolve os documentos já normalizados, um arquivo de cada vez e sempre na mesma ordem. O fluxo devolvido precisa ser fechado; arquivo ruim vira FalhaDeLeitura e o lote continua, e só a falta de sal para tudo.
public final class LeitorLote {

    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final String EXTENSAO_DE_PACOTE = ".zip";

    private final LeitorDocumentoFiscal leitor;
    private final NormalizadorDocumento normalizador;
    private final RegistroDeFalhasDeLeitura registroDeFalhas;
    private final RegistroDeDescricoesDeProduto registroDeDescricoes;

    // Construtor que recebe o leitor de XML, o normalizador e os registros de falhas e de descrições. Mudou na Etapa 11: ganhou o registro de descrições, sem valor padrão; quem não quer registrar passa RegistroDeDescricoesDeProduto.DESCARTA.
    public LeitorLote(LeitorDocumentoFiscal leitor,
                      NormalizadorDocumento normalizador,
                      RegistroDeFalhasDeLeitura registroDeFalhas,
                      RegistroDeDescricoesDeProduto registroDeDescricoes) {
        if (leitor == null || normalizador == null
                || registroDeFalhas == null || registroDeDescricoes == null) {
            throw new IllegalArgumentException(
                    "O leitor de lote exige leitor de documento, normalizador, registro de falhas e "
                            + "registro de descrições.");
        }
        this.leitor = leitor;
        this.normalizador = normalizador;
        this.registroDeFalhas = registroDeFalhas;
        this.registroDeDescricoes = registroDeDescricoes;
    }

    // Lê o lote de uma pasta, procurando .xml também nas subpastas, ou de um .zip; recusa outra coisa.
    public Stream<DocumentoComItens> ler(Path origem) throws IOException {
        if (origem == null) {
            throw new IllegalArgumentException("Não há origem de lote a ler.");
        }
        if (Files.isDirectory(origem)) {
            return lerDiretorio(origem);
        }
        if (ehPacote(origem)) {
            return lerPacote(origem);
        }
        throw new IllegalArgumentException(
                ("A origem do lote precisa ser um diretório ou um arquivo \"%s\", e \"%s\" não é nenhum dos "
                        + "dois.").formatted(EXTENSAO_DE_PACOTE, origem));
    }

    // Método auxiliar que lê os .xml da pasta, em ordem.
    private Stream<DocumentoComItens> lerDiretorio(Path diretorio) throws IOException {
        Stream<Path> percurso = Files.walk(diretorio);
        try {
            return percurso
                    .filter(Files::isRegularFile)
                    .filter(caminho -> ehDocumento(caminho.getFileName().toString()))
                    .sorted()
                    .map(this::documentoDoArquivo)
                    .flatMap(Optional::stream)
                    .onClose(percurso::close);
        } catch (RuntimeException erro) {
            percurso.close();
            throw erro;
        }
    }

    // Método auxiliar que lê os .xml do .zip, em ordem, e fecha o pacote junto com o fluxo.
    private Stream<DocumentoComItens> lerPacote(Path pacote) throws IOException {
        ZipFile arquivoCompactado = new ZipFile(pacote.toFile(), StandardCharsets.UTF_8);
        try {
            return arquivoCompactado.stream()
                    .filter(entrada -> !entrada.isDirectory())
                    .filter(entrada -> ehDocumento(entrada.getName()))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .map(entrada -> documentoDaEntrada(arquivoCompactado, entrada))
                    .flatMap(Optional::stream)
                    .onClose(() -> fechar(arquivoCompactado));
        } catch (RuntimeException erro) {
            fechar(arquivoCompactado);
            throw erro;
        }
    }

    // Método auxiliar que lê um arquivo da pasta; se falhar, registra a falha, menos quando falta o sal.
    private Optional<DocumentoComItens> documentoDoArquivo(Path caminho) {
        try (InputStream conteudo = new BufferedInputStream(Files.newInputStream(caminho))) {
            return Optional.of(normalizador.normalizar(leitor.ler(conteudo), registroDeDescricoes));
        } catch (IOException erro) {
            return registrar(caminho.toString(), erro);
        } catch (SalDeInstalacaoInvalido erro) {
            throw erro;
        } catch (RuntimeException erro) {
            return registrar(caminho.toString(), erro);
        }
    }

    // Método auxiliar que lê uma entrada do .zip; se falhar, registra a falha, menos quando falta o sal.
    private Optional<DocumentoComItens> documentoDaEntrada(ZipFile arquivoCompactado, ZipEntry entrada) {
        String origem = "%s!%s".formatted(arquivoCompactado.getName(), entrada.getName());
        try (InputStream conteudo = new BufferedInputStream(arquivoCompactado.getInputStream(entrada))) {
            return Optional.of(normalizador.normalizar(leitor.ler(conteudo), registroDeDescricoes));
        } catch (IOException erro) {
            return registrar(origem, erro);
        } catch (SalDeInstalacaoInvalido erro) {
            throw erro;
        } catch (RuntimeException erro) {
            return registrar(origem, erro);
        }
    }

    // Método auxiliar que registra a falha e devolve vazio.
    private Optional<DocumentoComItens> registrar(String origem, Throwable erro) {
        registroDeFalhas.registrar(FalhaDeLeitura.de(origem, erro));
        return Optional.empty();
    }

    // Método auxiliar que diz se o nome termina em .xml.
    private static boolean ehDocumento(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_DOCUMENTO);
    }

    // Método auxiliar que diz se a origem é um .zip.
    private static boolean ehPacote(Path origem) {
        return origem.getFileName() != null
                && origem.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_PACOTE);
    }

    // Método auxiliar que fecha o .zip.
    private static void fechar(ZipFile arquivoCompactado) {
        try {
            arquivoCompactado.close();
        } catch (IOException erro) {
            throw new UncheckedIOException(
                    "Não foi possível fechar o pacote " + arquivoCompactado.getName() + ".", erro);
        }
    }
}
