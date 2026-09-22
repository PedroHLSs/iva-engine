package br.edu.tcc.auditoria.infraestrutura.upload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Onde o arquivo enviado vira algo que o pipeline da Etapa 4 sabe ler. */
class AreaDaAnaliseTest {

    private static final LimitesDeUpload PADRAO = LimitesDeUpload.padrao();

    @Test
    void documentoAvulsoDeveVirarUmDiretorioComUmArquivoSo() throws IOException {
        try (AreaDaAnalise area = receber("nota.xml", "documento ficticio")) {
            assertThat(Files.isDirectory(area.origem()))
                    .describedAs("LeitorLote e ResumoDaEntrada aceitam diretório ou .zip, e recusam "
                            + "um .xml solto")
                    .isTrue();
            assertThat(area.ehPacote()).isFalse();
            try (var conteudo = Files.list(area.origem())) {
                assertThat(conteudo).hasSize(1);
            }
        }
    }

    @Test
    void pacoteDeveChegarAoPipelineComoOProprioArquivo() throws IOException {
        try (AreaDaAnalise area = receberPacote()) {
            assertThat(Files.isRegularFile(area.origem())).isTrue();
            assertThat(area.origem().getFileName().toString()).endsWith(".zip");
            assertThat(area.ehPacote()).isTrue();
        }
    }

    @Test
    void oNomeEnviadoNaoDeveVirarCaminho() throws IOException {
        String nomeHostil = "../../fora.xml";

        try (AreaDaAnalise area = receber(nomeHostil, "documento ficticio")) {
            try (var conteudo = Files.list(area.origem())) {
                assertThat(conteudo)
                        .describedAs("o nome enviado decide a extensão e nada mais")
                        .allSatisfy(caminho -> assertThat(caminho.getFileName().toString())
                                .isEqualTo("enviado.xml"));
            }
        }
    }

    @Test
    void deveRecusarRarComMensagemQuePedeZip() {
        assertThatThrownBy(() -> receber("acervo.rar", "conteudo ficticio"))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining(".zip")
                .hasMessageContaining("proprietário");
    }

    @Test
    void deveRecusarExtensaoQueNaoELidaPorEsteSistema() {
        assertThatThrownBy(() -> receber("planilha.xlsx", "conteudo ficticio"))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining(".xml");
    }

    @Test
    void deveRecusarArquivoVazio() {
        assertThatThrownBy(() -> receber("nota.xml", ""))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("vazio");
    }

    @Test
    void deveRecusarEnvioAcimaDoTeto() {
        LimitesDeUpload apertado = new LimitesDeUpload(64, 10, 1024, 4096, 500, 1024);

        assertThatThrownBy(() -> AreaDaAnalise.receber(
                "nota.xml", fluxo("x".repeat(200)), apertado))
                .isInstanceOf(PacoteRecusado.class)
                .hasMessageContaining("passa de");
    }

    @Test
    void deveApagarAAreaAoFechar() throws IOException {
        Path raiz;
        try (AreaDaAnalise area = receber("nota.xml", "documento ficticio")) {
            raiz = area.origem().getParent();
            assertThat(Files.exists(raiz)).isTrue();
        }
        assertThat(Files.exists(raiz))
                .describedAs("o temporário some quando a análise termina")
                .isFalse();
    }

    @Test
    void deveApagarAAreaQuandoOPacoteERecusado() throws IOException {
        long antes = temporariosDaAnalise();

        assertThatThrownBy(() -> AreaDaAnalise.receber(
                "acervo.zip", fluxo("isto nao e um pacote"), PADRAO))
                .isInstanceOf(PacoteRecusado.class);

        assertThat(temporariosDaAnalise())
                .describedAs("falhar no meio não pode deixar lixo para trás")
                .isEqualTo(antes);
    }

    private static AreaDaAnalise receber(String nome, String conteudo) {
        return AreaDaAnalise.receber(nome, fluxo(conteudo), PADRAO);
    }

    private static AreaDaAnalise receberPacote() throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("nota.xml"));
            zip.write("documento ficticio".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return AreaDaAnalise.receber(
                "acervo.zip", new ByteArrayInputStream(bytes.toByteArray()), PADRAO);
    }

    private static InputStream fluxo(String conteudo) {
        return new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private static long temporariosDaAnalise() throws IOException {
        Path temporario = Path.of(System.getProperty("java.io.tmpdir"));
        try (var conteudo = Files.list(temporario)) {
            return conteudo
                    .filter(caminho -> caminho.getFileName().toString().startsWith("analise-ibs-cbs-"))
                    .count();
        }
    }
}
