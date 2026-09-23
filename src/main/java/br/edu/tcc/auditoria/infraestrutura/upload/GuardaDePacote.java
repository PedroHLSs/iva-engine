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

// Classe que confere um .zip recebido pela web antes de ele ser lido: quantidade de entradas, tamanho real de cada XML, total descomprimido, razão de compressão, caminho que tenta sair da pasta e pelo menos um .xml. Nenhuma entrada é extraída hoje; a checagem de caminho fica para proteger quem um dia escrever a extração.
public final class GuardaDePacote {

    private static final String EXTENSAO_DE_DOCUMENTO = ".xml";
    private static final int TAMANHO_DO_BUFFER = 8192;

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private GuardaDePacote() {
    }

    // Método estático que confere o pacote inteiro; recusa se passar de algum limite, se estiver corrompido ou se não tiver nenhum documento.
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

    // Método auxiliar que mede o tamanho real da entrada descomprimindo, e para no instante em que passa do limite, que é o que protege contra bomba de descompressão.
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

    // Método auxiliar que confere só o tamanho declarado de uma entrada que não é XML, porque ela nunca vai ser descomprimida.
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

    // Método auxiliar que recusa entrada grande que se expande demais em relação ao tamanho comprimido.
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

    // Método auxiliar que recusa entrada com caminho absoluto, com letra de unidade do Windows ou que sobe para fora do pacote.
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

    // Método auxiliar que diz se o nome termina em .xml.
    private static boolean ehDocumento(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith(EXTENSAO_DE_DOCUMENTO);
    }

    // Método auxiliar que prepara o nome da entrada para a mensagem de erro.
    private static String nomeParaMensagem(ZipEntry entrada) {
        return nomeParaMensagem(entrada.getName());
    }

    // Método auxiliar que corta o nome para a mensagem de erro e esconde a chave de acesso, porque ela contém o CNPJ do emitente e a mensagem vai para a rede e para o log.
    private static String nomeParaMensagem(String nome) {
        String semChave = nome.replaceAll("[0-9]{44}", "(chave de acesso omitida)");
        return semChave.length() <= 120 ? semChave : semChave.substring(0, 120) + "...";
    }

    // Método auxiliar que devolve a barra invertida do Windows.
    private static char separadorDoWindows() {
        return (char) 92;
    }
}
