package br.edu.tcc.auditoria.infraestrutura.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

// Classe que grava o arquivo enviado pela web numa pasta temporária, fora do repositório, para o mesmo leitor da linha de comando ler, e apaga tudo no close(), mesmo se a análise falhar. O nome enviado só decide a extensão: o conteúdo é gravado com nome fixo, então o nome não vira caminho.
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

    // Construtor privado: a área só é criada por receber().
    private AreaDaAnalise(Path raiz, Path origem, boolean pacote) {
        this.raiz = raiz;
        this.origem = origem;
        this.pacote = pacote;
    }

    // Método estático que recebe o arquivo enviado, confere a extensão e o tamanho e deixa pronto para a leitura; pacote .zip passa também pelo GuardaDePacote.
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

    // Retorna o caminho entregue à leitura: a pasta do XML, ou o próprio pacote.
    public Path origem() {
        return origem;
    }

    // Diz se o que chegou foi um pacote .zip.
    public boolean ehPacote() {
        return pacote;
    }

    // Apaga a pasta temporária inteira.
    @Override
    public void close() {
        apagar(raiz);
    }

    // Método auxiliar que decide pela extensão se é pacote ou XML avulso; recusa .rar e qualquer outra extensão.
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

    // Método auxiliar que grava o arquivo e para no instante em que passa do limite; recusa também arquivo vazio.
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

    // Método auxiliar que cria a pasta temporária da análise.
    private static Path criarRaiz() {
        try {
            return Files.createTempDirectory(PREFIXO);
        } catch (IOException semTemporario) {
            throw new PacoteRecusado(
                    "Não foi possível criar o diretório temporário da análise.", semTemporario);
        }
    }

    // Método auxiliar que apaga a pasta, do mais fundo para o mais raso. Se não conseguir apagar, não derruba a análise: sobra um temporário, o que acontece no OneDrive.
    private static void apagar(Path raiz) {
        if (raiz == null || !Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> conteudo = Files.walk(raiz)) {
            conteudo.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException naoApagou) {
                    // Sobra um arquivo temporário, mas a análise não se perde.
                }
            });
        } catch (IOException naoPercorreu) {
            // Idem.
        }
    }
}
