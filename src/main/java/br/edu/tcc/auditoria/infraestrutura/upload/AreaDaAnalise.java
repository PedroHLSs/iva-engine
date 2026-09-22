package br.edu.tcc.auditoria.infraestrutura.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Onde o arquivo enviado pela web vira algo que o pipeline da Etapa 4 sabe ler,
 * e de onde ele some quando a análise termina.
 *
 * <h2>Por que existe um diretório para um XML só</h2>
 *
 * <p>{@code LeitorLote} e {@code ResumoDaEntrada} aceitam um diretório ou um
 * {@code .zip}, e recusam um {@code .xml} solto — foram escritos para acervo, não
 * para arquivo avulso. Em vez de alargar os dois, o XML enviado é gravado dentro
 * de um diretório de um arquivo só. O caminho de leitura fica idêntico ao da CLI,
 * byte por byte, e nenhuma linha da Etapa 4 muda.</p>
 *
 * <h2>O nome que a pessoa enviou não vira caminho</h2>
 *
 * <p>O nome do arquivo enviado é texto controlado por quem envia. Ele é usado
 * para <em>decidir a extensão</em> e para nada mais: o conteúdo é sempre gravado
 * com nome fixo. Assim não há travessia de caminho possível pelo nome, e o
 * resumo da entrada passa a depender só do conteúdo — o mesmo documento enviado
 * duas vezes com nomes diferentes produz o mesmo resumo, que é o comportamento
 * certo.</p>
 *
 * <h2>Fora do repositório, sempre</h2>
 *
 * <p>O diretório é criado no temporário do sistema operacional, nunca dentro do
 * projeto nem de {@code src/}, e é apagado em {@code close()} — inclusive quando
 * a análise falha no meio.</p>
 */
public final class AreaDaAnalise implements AutoCloseable {

    private static final String PREFIXO = "analise-ibs-cbs-";
    private static final String NOME_DO_DOCUMENTO = "enviado.xml";
    private static final String NOME_DO_PACOTE = "enviado.zip";
    private static final String SUBDIRETORIO_DE_DOCUMENTOS = "documentos";

    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final String EXTENSAO_DE_PACOTE = ".zip";
    private static final String EXTENSAO_RECUSADA = ".rar";

    private static final int TAMANHO_DO_BUFFER = 8192;

    private final Path raiz;
    private final Path origem;
    private final boolean pacote;

    private AreaDaAnalise(Path raiz, Path origem, boolean pacote) {
        this.raiz = raiz;
        this.origem = origem;
        this.pacote = pacote;
    }

    /**
     * Recebe o que foi enviado, confere e deixa pronto para o pipeline.
     *
     * @param nomeEnviado nome original, usado apenas para decidir a extensão
     * @param conteudo    fluxo do arquivo; não é fechado aqui, quem abriu fecha
     * @param limites     os tetos desta instalação
     * @throws PacoteRecusado quando a extensão não é aceita, o tamanho estoura ou
     *                        o pacote não passa no {@link GuardaDePacote}
     */
    public static AreaDaAnalise receber(
            String nomeEnviado, InputStream conteudo, LimitesDeUpload limites) {

        if (conteudo == null || limites == null) {
            throw new IllegalArgumentException("A área da análise precisa do conteúdo e dos limites.");
        }
        boolean ehPacote = conferirExtensao(nomeEnviado);

        Path raiz = criarRaiz();
        try {
            Path destino = ehPacote
                    ? raiz.resolve(NOME_DO_PACOTE)
                    : Files.createDirectory(raiz.resolve(SUBDIRETORIO_DE_DOCUMENTOS))
                            .resolve(NOME_DO_DOCUMENTO);

            gravarComTeto(conteudo, destino, limites);

            if (ehPacote) {
                GuardaDePacote.conferir(destino, limites);
                return new AreaDaAnalise(raiz, destino, true);
            }
            return new AreaDaAnalise(raiz, destino.getParent(), false);
        } catch (RuntimeException | IOException falha) {
            apagar(raiz);
            if (falha instanceof PacoteRecusado recusa) {
                throw recusa;
            }
            if (falha instanceof RuntimeException erro) {
                throw erro;
            }
            throw new PacoteRecusado("Não foi possível gravar o arquivo enviado para análise.", falha);
        }
    }

    /** O caminho a entregar ao pipeline: o diretório do XML, ou o próprio pacote. */
    public Path origem() {
        return origem;
    }

    /** Se o que chegou foi um pacote. A tela de resultado é outra num caso e noutro. */
    public boolean ehPacote() {
        return pacote;
    }

    @Override
    public void close() {
        apagar(raiz);
    }

    /**
     * Decide a extensão, e recusa o que não é lido.
     *
     * @return {@code true} para pacote, {@code false} para documento avulso
     */
    private static boolean conferirExtensao(String nomeEnviado) {
        String nome = nomeEnviado == null ? "" : nomeEnviado.toLowerCase(Locale.ROOT).strip();
        if (nome.endsWith(EXTENSAO_DE_PACOTE)) {
            return true;
        }
        if (nome.endsWith(EXTENSAO_DE_DOCUMENTO)) {
            return false;
        }
        if (nome.endsWith(EXTENSAO_RECUSADA)) {
            throw new PacoteRecusado(
                    "Arquivos .rar não são lidos. Reempacote o acervo como .zip e envie de novo. "
                            + "O formato é proprietário e não há biblioteca Java confiável para a "
                            + "versão atual dele; acrescentar essa dependência não se justifica num "
                            + "caso que praticamente não ocorre, porque ERP exporta em .zip.");
        }
        throw new PacoteRecusado(
                "Envie um arquivo .xml de documento fiscal ou um .zip com vários deles.");
    }

    /**
     * Grava o fluxo parando no instante em que o teto é ultrapassado.
     *
     * <p>O contêiner de servlet já tem o próprio limite de multipart, e ele age
     * antes deste na maior parte dos casos. Este existe porque a mensagem dele é
     * genérica e porque a classe precisa ser correta sozinha — ela é chamada
     * direto pelos testes, sem servidor nenhum na frente.</p>
     */
    private static void gravarComTeto(InputStream conteudo, Path destino, LimitesDeUpload limites)
            throws IOException {

        long gravados = 0;
        byte[] buffer = new byte[TAMANHO_DO_BUFFER];
        try (OutputStream saida = Files.newOutputStream(destino)) {
            int lidos;
            while ((lidos = conteudo.read(buffer)) != -1) {
                gravados += lidos;
                if (gravados > limites.tamanhoMaximoDoEnvio()) {
                    throw new PacoteRecusado(
                            ("O arquivo enviado passa de %s. Divida o acervo em envios menores.")
                                    .formatted(limites.envioEmMegabytes()));
                }
                saida.write(buffer, 0, lidos);
            }
        }
        if (gravados == 0) {
            throw new PacoteRecusado("O arquivo enviado está vazio.");
        }
    }

    private static Path criarRaiz() {
        try {
            return Files.createTempDirectory(PREFIXO);
        } catch (IOException semTemporario) {
            throw new PacoteRecusado(
                    "Não foi possível criar o diretório temporário da análise.", semTemporario);
        }
    }

    /**
     * Apaga a área inteira, do mais fundo para o mais raso.
     *
     * <p>Falha ao apagar não derruba a análise: o arquivo temporário sobrando é
     * incômodo, e perder um resultado já calculado por causa dele seria pior.
     * Dentro do OneDrive isso acontece de verdade — o sincronizador segura
     * arquivo recém-escrito, que é o mesmo motivo pelo qual {@code mvn clean}
     * falha neste repositório.</p>
     */
    private static void apagar(Path raiz) {
        if (raiz == null || !Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> conteudo = Files.walk(raiz)) {
            conteudo.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException naoApagou) {
                    // Ver o Javadoc: sobra temporário, não se perde análise.
                }
            });
        } catch (IOException naoPercorreu) {
            // Idem.
        }
    }
}
