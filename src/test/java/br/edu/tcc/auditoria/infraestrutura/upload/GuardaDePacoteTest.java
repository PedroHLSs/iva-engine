package br.edu.tcc.auditoria.infraestrutura.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O guarda do pacote, exercitado com pacotes montados na hora.
 *
 * <p>Todos os conteúdos são fictícios e nenhum é documento fiscal: o guarda não
 * lê XML, ele confere forma e tamanho.</p>
 *
 * <h2>Um limite deste teste, dito em voz alta</h2>
 *
 * <p>O guarda <em>mede</em> a entrada descomprimindo-a, em vez de acreditar no
 * tamanho declarado no diretório central do pacote — que é escrito por quem
 * montou o arquivo e pode mentir. Os testes abaixo exercitam o teto sobre
 * conteúdo real; <strong>não há aqui um pacote com diretório central
 * forjado</strong>, porque a biblioteca padrão não escreve um, e falsificá-lo a
 * bytes seria um teste sobre o formato do zip, não sobre este guarda. A medição
 * está no código e é visível ali; esta suíte não a prova.</p>
 */
class GuardaDePacoteTest {

    @TempDir
    private Path pasta;

    private static final LimitesDeUpload PADRAO = LimitesDeUpload.padrao();

    /** Limites apertados, para os testes de teto não precisarem de arquivos grandes. */
    private static final LimitesDeUpload APERTADOS = new LimitesDeUpload(
            1024L * 1024L, 3, 2048, 4096, 500, 1024L * 1024L);

    @Test
    void deveAceitarPacoteComDocumentos() throws IOException {
        Path pacote = zipCom(Map.of(
                "nota-1.xml", conteudo("documento ficticio 1"),
                "nota-2.xml", conteudo("documento ficticio 2")));

        assertThatCode(() -> GuardaDePacote.conferir(pacote, PADRAO)).doesNotThrowAnyException();
    }

    @Test
    void deveIgnorarDiretorioEArquivoQueNaoEDocumentoSemFalharOLote() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("2026-01/", new byte[0]);
        entradas.put("2026-01/leiame.txt", conteudo("texto ficticio"));
        entradas.put("2026-01/nota.xml", conteudo("documento ficticio"));

        Path pacote = zipCom(entradas);

        assertThatCode(() -> GuardaDePacote.conferir(pacote, PADRAO)).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarPacoteSemNenhumDocumento() throws IOException {
        Path pacote = zipCom(Map.of("leiame.txt", conteudo("so texto ficticio")));

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, PADRAO))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("análise de nada");
    }

    @Test
    void deveRecusarPacoteComEntradasDemais() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        for (int numero = 1; numero <= 4; numero++) {
            entradas.put("nota-%d.xml".formatted(numero), conteudo("documento ficticio"));
        }
        Path pacote = zipCom(entradas);

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, APERTADOS))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("mais de 3 entradas");
    }

    @Test
    void deveRecusarDocumentoQueEstouraOTetoDescomprimido() throws IOException {
        Path pacote = zipCom(Map.of("grande.xml", new byte[8192]));

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, APERTADOS))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("depois de descomprimida");
    }

    @Test
    void deveRecusarPacoteCujoTotalDescomprimidoEstoura() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("um.xml", new byte[2000]);
        entradas.put("dois.xml", new byte[2000]);
        entradas.put("tres.xml", new byte[2000]);
        Path pacote = zipCom(entradas);

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, APERTADOS))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("depois de descomprimidos");
    }

    @Test
    void deveRecusarEntradaComRazaoDeCompressaoAlemDoRazoavel() throws IOException {
        // Dois megabytes de zeros comprimem para poucos kilobytes: razão na casa
        // do milhar, muito acima do limite, e acima do piso de conferência.
        Path pacote = zipCom(Map.of("bomba.xml", new byte[2 * 1024 * 1024]));

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, PADRAO))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("vezes o tamanho comprimido");
    }

    /**
     * O piso existe para não recusar dado legítimo: XML de documento fiscal
     * comprime muito, e razão alta num arquivo pequeno não é ameaça nenhuma.
     */
    @Test
    void naoDeveConferirRazaoAbaixoDoPiso() throws IOException {
        Path pacote = zipCom(Map.of("pequeno.xml", new byte[64 * 1024]));

        assertThatCode(() -> GuardaDePacote.conferir(pacote, PADRAO))
                .describedAs("64 KB de conteúdo repetido têm razão altíssima e são inofensivos")
                .doesNotThrowAnyException();
    }

    @Test
    void deveRecusarEntradaQueApontaParaForaDoPacote() throws IOException {
        Path pacote = zipCom(Map.of("../fora.xml", conteudo("documento ficticio")));

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, PADRAO))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("aponta para fora do pacote");
    }

    @Test
    void deveRecusarEntradaComCaminhoAbsoluto() throws IOException {
        Path pacote = zipCom(Map.of("/raiz/nota.xml", conteudo("documento ficticio")));

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, PADRAO))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("caminho absoluto");
    }

    @Test
    void deveRecusarArquivoQueNaoEUmPacote() throws IOException {
        Path naoEZip = pasta.resolve("nao-e-zip.zip");
        Files.write(naoEZip, conteudo("isto nao e um pacote"));

        assertThatThrownBy(() -> GuardaDePacote.conferir(naoEZip, PADRAO))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("corrompido");
    }

    /**
     * Nome de entrada é texto de quem enviou: a mensagem de recusa não pode
     * devolver a chave de acesso, cujos dígitos do meio são o CNPJ do emitente.
     */
    @Test
    void aMensagemDeRecusaNaoDevolveAChaveDeAcessoQueVeioNoNome() throws IOException {
        String chave = "9".repeat(44);
        Path pacote = zipCom(Map.of("../" + chave + "-nfe.xml", conteudo("documento ficticio")));

        assertThatThrownBy(() -> GuardaDePacote.conferir(pacote, PADRAO))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageNotContaining(chave)
                .hasMessageContaining("chave de acesso omitida");
    }

    private static byte[] conteudo(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    private Path zipCom(Map<String, byte[]> entradas) throws IOException {
        Path pacote = pasta.resolve("pacote-%d.zip".formatted(entradas.hashCode() & 0xffff));
        try (OutputStream arquivo = Files.newOutputStream(pacote);
             ZipOutputStream zip = new ZipOutputStream(arquivo, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        return pacote;
    }
}
