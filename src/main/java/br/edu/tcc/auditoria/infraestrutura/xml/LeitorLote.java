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

/**
 * Lê um lote de documentos fiscais — um diretório ou um arquivo ZIP — e devolve
 * os documentos já normalizados.
 *
 * <h2>Um arquivo de cada vez</h2>
 *
 * <p>O fluxo devolvido é preguiçoso: nenhum arquivo é aberto antes de ser
 * pedido, e cada um é fechado antes do seguinte ser aberto. Um acervo de dezenas
 * de milhares de notas passa por aqui sem que o conjunto inteiro exista em
 * memória em momento algum. Só a lista de nomes de arquivo é materializada, para
 * que a ordem de processamento seja a mesma em toda execução — listagem de
 * diretório não promete ordem, e relatório de auditoria precisa ser
 * comparável.</p>
 *
 * <p><strong>O fluxo precisa ser fechado.</strong> Ele segura o percurso do
 * diretório ou o arquivo ZIP aberto, e só os libera no fechamento:</p>
 *
 * <pre>{@code
 * try (Stream<DocumentoComItens> documentos = leitorLote.ler(caminho)) {
 *     documentos.forEach(...);
 * }
 * }</pre>
 *
 * <h2>Arquivo ruim não derruba o lote</h2>
 *
 * <p>Arquivo malformado, arquivo que não é NF-e, documento cuja chave ou cujo
 * NCM não têm a forma que o domínio exige: cada um vira uma
 * {@link FalhaDeLeitura} no {@link RegistroDeFalhasDeLeitura} e o lote continua.
 * A única exceção é a falta do sal de pseudonimização, que é erro de
 * configuração da instalação e vale para todos os arquivos — essa interrompe,
 * porque registrá-la mil vezes não ajudaria ninguém.</p>
 */
public final class LeitorLote {

    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final String EXTENSAO_DE_PACOTE = ".zip";

    private final LeitorDocumentoFiscal leitor;
    private final NormalizadorDocumento normalizador;
    private final RegistroDeFalhasDeLeitura registroDeFalhas;
    private final RegistroDeDescricoesDeProduto registroDeDescricoes;

    /*
     * Emenda da etapa de conferência, sobre a Etapa 4.
     *
     * O construtor ganhou o registro de descrições, que é a segunda saída lateral
     * desta leitura — a primeira são as falhas. Os dois têm escopo de lote, e não
     * de processo: quem os cria é quem começa uma análise.
     *
     * Não há construtor sem ele. Um valor padrão faria um caminho de leitura
     * deixar de registrar descrição sem que ninguém tivesse decidido isso; quem
     * não quer registrar passa RegistroDeDescricoesDeProduto.DESCARTA, que é uma
     * decisão escrita e localizável por busca.
     */
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

    /**
     * Lê o lote na origem informada, que pode ser um diretório ou um ZIP.
     *
     * <p>Em diretório, a busca é recursiva e considera todo arquivo com extensão
     * {@code .xml}. Em ZIP, todo arquivo com extensão {@code .xml} dentro do
     * pacote, inclusive em subpastas. ZIP dentro de diretório não é aberto — o
     * lote é um ou outro.</p>
     *
     * @throws IOException              se a própria origem não puder ser aberta
     * @throws IllegalArgumentException se a origem não for diretório nem ZIP
     */
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

    private Optional<DocumentoComItens> registrar(String origem, Throwable erro) {
        registroDeFalhas.registrar(FalhaDeLeitura.de(origem, erro));
        return Optional.empty();
    }

    private static boolean ehDocumento(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_DOCUMENTO);
    }

    private static boolean ehPacote(Path origem) {
        return origem.getFileName() != null
                && origem.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_PACOTE);
    }

    private static void fechar(ZipFile arquivoCompactado) {
        try {
            arquivoCompactado.close();
        } catch (IOException erro) {
            throw new UncheckedIOException(
                    "Não foi possível fechar o pacote " + arquivoCompactado.getName() + ".", erro);
        }
    }
}
