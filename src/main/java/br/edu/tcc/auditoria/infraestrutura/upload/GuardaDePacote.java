package br.edu.tcc.auditoria.infraestrutura.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Confere um pacote recebido pela web antes de qualquer coisa lê-lo.
 *
 * <p>Um {@code .zip} vindo de fora é entrada não confiável. O leitor da Etapa 4
 * foi escrito para um diretório de trabalho de quem opera a máquina; agora o
 * mesmo leitor recebe arquivo de quem quiser mandar um. Este guarda existe para
 * que a diferença não seja apenas de confiança.</p>
 *
 * <h2>O que ele confere</h2>
 *
 * <ol>
 *   <li><strong>Quantidade de entradas.</strong> Um pacote com centenas de
 *       milhares de entradas trava o processamento sem precisar de bomba
 *       nenhuma.</li>
 *   <li><strong>Tamanho descomprimido de cada documento, medido.</strong> Não o
 *       tamanho declarado no diretório central do zip — esse é escrito por quem
 *       montou o arquivo e mente de graça. O guarda descomprime e conta,
 *       parando no instante em que o teto é ultrapassado.</li>
 *   <li><strong>Total descomprimido do pacote.</strong> Mil arquivos de tamanho
 *       aceitável somam um tamanho inaceitável.</li>
 *   <li><strong>Razão de compressão</strong>, acima de um piso — ver
 *       {@link LimitesDeUpload#padrao()} para por que o piso existe.</li>
 *   <li><strong>Caminho que escapa do diretório</strong> — ver abaixo.</li>
 *   <li><strong>Ao menos um {@code .xml}.</strong> Pacote sem documento nenhum
 *       produziria uma análise de nada, com aparência de análise feita.</li>
 * </ol>
 *
 * <h2>Sobre o caminho que escapa, com honestidade</h2>
 *
 * <p><strong>Esta verificação não conserta uma falha existente.</strong> O
 * {@code LeitorLote} da Etapa 4 não extrai coisa alguma: ele lê cada entrada
 * direto do {@code ZipFile}, por {@code getInputStream}, e nunca escreve no
 * disco a partir do nome da entrada. Não há, hoje, caminho pelo qual um nome
 * como {@code ../../etc/senha} produza escrita fora de lugar.</p>
 *
 * <p>A conferência entra assim mesmo, e o motivo é que a propriedade que
 * protege é frágil por natureza: ela depende de ninguém, um dia, escrever a
 * extração. Registrar isso como verificação — e não como comentário — é o que
 * faz a decisão sobreviver a quem não leu esta classe.</p>
 *
 * <h2>Entradas que não são documento</h2>
 *
 * <p>São contadas e têm o tamanho declarado conferido, mas não são
 * descomprimidas. Nem este guarda, nem o {@code LeitorLote}, nem o
 * {@code ResumoDaEntrada} expandem entrada que não termine em {@code .xml} — de
 * modo que uma bomba escondida numa delas nunca chega a explodir. Gastar um
 * passo de descompressão para conferir o que ninguém vai descomprimir seria
 * trabalho sem propriedade a proteger.</p>
 */
public final class GuardaDePacote {

    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final int TAMANHO_DO_BUFFER = 8192;

    private GuardaDePacote() {
    }

    /**
     * Confere o pacote inteiro, ou recusa.
     *
     * @throws PacoteRecusado quando qualquer um dos limites é ultrapassado, o
     *                        pacote está corrompido, ou não há documento nenhum
     */
    public static void conferir(Path pacote, LimitesDeUpload limites) {
        if (pacote == null || limites == null) {
            throw new IllegalArgumentException("O guarda precisa do pacote e dos limites.");
        }

        long entradas = 0;
        long totalDescomprimido = 0;
        int documentos = 0;

        try (ZipFile zip = new ZipFile(pacote.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> todas = zip.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();

                entradas++;
                if (entradas > limites.entradasMaximasNoPacote()) {
                    throw new PacoteRecusado(
                            ("O pacote tem mais de %d entradas. Divida o acervo em pacotes menores: "
                                    + "um lote desse tamanho leva mais tempo para ser lido do que "
                                    + "para ser conferido.")
                                    .formatted(limites.entradasMaximasNoPacote()));
                }

                recusarCaminhoQueEscapa(entrada.getName());

                if (entrada.isDirectory()) {
                    continue;
                }
                if (!ehDocumento(entrada.getName())) {
                    conferirTamanhoDeclarado(entrada, limites);
                    continue;
                }

                documentos++;
                long medido = medirDescomprimido(zip, entrada, limites);
                conferirRazao(entrada, medido, limites);

                totalDescomprimido += medido;
                if (totalDescomprimido > limites.totalDescomprimidoMaximo()) {
                    throw new PacoteRecusado(
                            ("Os documentos do pacote passam de %s depois de descomprimidos. Divida o "
                                    + "acervo em pacotes menores.").formatted(limites.totalEmMegabytes()));
                }
            }
        } catch (ZipException pacoteCorrompido) {
            throw new PacoteRecusado(
                    "O arquivo não pôde ser aberto como .zip. Ele pode estar corrompido, incompleto "
                            + "ou protegido por senha.",
                    pacoteCorrompido);
        } catch (IOException erroDeLeitura) {
            throw new PacoteRecusado(
                    "Não foi possível ler o pacote enviado.", erroDeLeitura);
        }

        if (documentos == 0) {
            throw new PacoteRecusado(
                    ("O pacote não tem nenhum arquivo \"%s\". Só documentos fiscais em XML são lidos; "
                            + "um pacote sem nenhum produziria uma análise de nada, com aparência de "
                            + "análise feita.").formatted(EXTENSAO_DE_DOCUMENTO));
        }
    }

    /**
     * Mede o tamanho real da entrada descomprimindo-a, e para no instante em que
     * o teto é ultrapassado.
     *
     * <p>Parar no instante importa: continuar até o fim para depois comparar é
     * exatamente o que uma bomba de descompressão explora.</p>
     */
    private static long medirDescomprimido(ZipFile zip, ZipEntry entrada, LimitesDeUpload limites)
            throws IOException {

        long medido = 0;
        byte[] descarte = new byte[TAMANHO_DO_BUFFER];
        try (InputStream conteudo = zip.getInputStream(entrada)) {
            int lidos;
            while ((lidos = conteudo.read(descarte)) != -1) {
                medido += lidos;
                if (medido > limites.tamanhoMaximoPorEntrada()) {
                    throw new PacoteRecusado(
                            ("A entrada \"%s\" passa de %s depois de descomprimida. Documento fiscal "
                                    + "não chega a esse tamanho; um arquivo que chega ou não é "
                                    + "documento, ou foi montado para ocupar memória.")
                                    .formatted(nomeParaMensagem(entrada), limites.porEntradaEmMegabytes()));
                }
            }
        }
        return medido;
    }

    /**
     * Entrada que ninguém vai descomprimir: basta o tamanho que o pacote declara.
     *
     * <p>Declarado, e não medido, de propósito — medir exigiria descomprimir, que
     * é o custo que esta verificação existe para não pagar. Se o valor declarado
     * mentir para menos, nada acontece: a entrada continua sem ser lida por
     * ninguém.</p>
     */
    private static void conferirTamanhoDeclarado(ZipEntry entrada, LimitesDeUpload limites) {
        long declarado = entrada.getSize();
        if (declarado > limites.tamanhoMaximoPorEntrada()) {
            throw new PacoteRecusado(
                    ("A entrada \"%s\" declara mais de %s. Ela não é um documento fiscal e não seria "
                            + "lida, mas um pacote com arquivo desse tamanho não é o que esta "
                            + "ferramenta espera receber.")
                            .formatted(nomeParaMensagem(entrada), limites.porEntradaEmMegabytes()));
        }
    }

    private static void conferirRazao(ZipEntry entrada, long medido, LimitesDeUpload limites) {
        if (medido < limites.pisoParaConferirRazao()) {
            return;
        }
        long comprimido = entrada.getCompressedSize();
        if (comprimido <= 0) {
            return;
        }
        long razao = medido / comprimido;
        if (razao > limites.razaoDeCompressaoMaxima()) {
            throw new PacoteRecusado(
                    ("A entrada \"%s\" expande %d vezes o tamanho comprimido dela, acima do limite de "
                            + "%d. XML comprime muito, mas não tanto: essa razão, num arquivo já "
                            + "grande depois de descomprimido, é assinatura de conteúdo montado para "
                            + "ocupar memória.")
                            .formatted(
                                    nomeParaMensagem(entrada),
                                    razao,
                                    limites.razaoDeCompressaoMaxima()));
        }
    }

    /**
     * Recusa entrada cujo caminho normalizado saia do diretório.
     *
     * <p>Nome absoluto, nome com unidade do Windows, e qualquer nome que depois
     * de normalizado comece a subir na árvore.</p>
     */
    private static void recusarCaminhoQueEscapa(String nome) {
        String comBarrasNormais = nome.replace(separadorDoWindows(), '/');
        if (comBarrasNormais.startsWith("/") || comBarrasNormais.matches("^[A-Za-z]:.*")) {
            throw new PacoteRecusado(
                    ("A entrada \"%s\" tem caminho absoluto. Entrada de pacote precisa ser relativa ao "
                            + "próprio pacote.").formatted(nomeParaMensagem(nome)));
        }
        Path normalizado;
        try {
            normalizado = Path.of(comBarrasNormais).normalize();
        } catch (InvalidPathException caminhoImpossivel) {
            throw new PacoteRecusado(
                    "Uma entrada do pacote tem nome que não é um caminho válido.", caminhoImpossivel);
        }
        if (normalizado.startsWith("..")) {
            throw new PacoteRecusado(
                    ("A entrada \"%s\" aponta para fora do pacote. Nenhuma entrada é extraída por esta "
                            + "ferramenta, mas pacote que tenta sair do próprio diretório não é pacote "
                            + "de documentos fiscais.").formatted(nomeParaMensagem(nome)));
        }
    }

    private static boolean ehDocumento(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_DOCUMENTO);
    }

    private static String nomeParaMensagem(ZipEntry entrada) {
        return nomeParaMensagem(entrada.getName());
    }

    /**
     * O nome da entrada como ele pode aparecer numa mensagem de erro.
     *
     * <p>Cortado, porque nome de entrada é texto de quem enviou e pode ser
     * arbitrariamente longo; e sem corrida de 44 dígitos, porque o nome de um
     * XML de NF-e costuma ser a chave de acesso, cujos dígitos do meio são o
     * CNPJ do emitente. A mensagem de recusa sai pela rede e entra em log.</p>
     */
    private static String nomeParaMensagem(String nome) {
        String semChave = nome.replaceAll("[0-9]{44}", "(chave de acesso omitida)");
        return semChave.length() <= 120 ? semChave : semChave.substring(0, 120) + "...";
    }

    private static char separadorDoWindows() {
        return (char) 92;
    }
}
