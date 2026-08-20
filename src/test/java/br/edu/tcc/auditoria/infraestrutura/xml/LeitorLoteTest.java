package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.dominio.Documento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorLoteTest {

    @TempDir
    private Path diretorio;

    private FalhasDeLeituraEmMemoria falhas;
    private LeitorLote leitorLote;

    @BeforeEach
    void prepararLeitor() {
        falhas = new FalhasDeLeituraEmMemoria();
        leitorLote = new LeitorLote(DocumentoDeTeste.leitor(), DocumentoDeTeste.normalizador(), falhas);
    }

    @Test
    void deveLerTodosOsDocumentosDeUmDiretorio() throws IOException {
        copiarParaODiretorio(DocumentoDeTeste.ITEM_COMPLETO, DocumentoDeTeste.NFCE_SEM_DESTINATARIO,
                DocumentoDeTeste.MULTIPLOS_ITENS);

        assertThat(chavesLidasDe(diretorio)).hasSize(3);
        assertThat(falhas.vazio()).isTrue();
    }

    @Test
    void deveLerEmOrdemDeterminista() throws IOException {
        copiarParaODiretorio(DocumentoDeTeste.MULTIPLOS_ITENS, DocumentoDeTeste.ITEM_COMPLETO,
                DocumentoDeTeste.NFCE_SEM_DESTINATARIO);

        assertThat(chavesLidasDe(diretorio))
                .as("a ordem sai do nome do arquivo, não da listagem do sistema de arquivos")
                .containsExactly(
                        "33333333333333333333333333333333333333333333",
                        "11111111111111111111111111111111111111111111",
                        "44444444444444444444444444444444444444444444");
    }

    @Test
    void deveEncontrarDocumentoEmSubdiretorio() throws IOException {
        Path subdiretorio = Files.createDirectories(diretorio.resolve("2026").resolve("01"));
        Files.copy(DocumentoDeTeste.caminho(DocumentoDeTeste.ITEM_COMPLETO),
                subdiretorio.resolve(DocumentoDeTeste.ITEM_COMPLETO));

        assertThat(chavesLidasDe(diretorio)).hasSize(1);
    }

    @Test
    void deveRegistrarAFalhaESeguirOLoteQuandoUmArquivoEIlegivel() throws IOException {
        copiarParaODiretorio(DocumentoDeTeste.ITEM_COMPLETO, DocumentoDeTeste.CORROMPIDO,
                DocumentoDeTeste.MULTIPLOS_ITENS);

        assertThat(chavesLidasDe(diretorio))
                .as("o arquivo ilegível não pode levar os outros junto")
                .hasSize(2);
        assertThat(falhas.falhas())
                .singleElement()
                .satisfies(falha -> {
                    assertThat(falha.origem()).endsWith(DocumentoDeTeste.CORROMPIDO);
                    assertThat(falha.tipoDeErro()).isEqualTo(DocumentoFiscalIlegivel.class.getSimpleName());
                    assertThat(falha.motivo()).isNotBlank();
                });
    }

    @Test
    void deveIgnorarArquivoQueNaoTemExtensaoDeDocumento() throws IOException {
        copiarParaODiretorio(DocumentoDeTeste.ITEM_COMPLETO);
        Files.writeString(diretorio.resolve("anotacoes.txt"), "isto não é documento", StandardCharsets.UTF_8);

        assertThat(chavesLidasDe(diretorio)).hasSize(1);
        assertThat(falhas.vazio())
                .as("arquivo que não é XML não é falha de leitura: não era para ser lido")
                .isTrue();
    }

    @Test
    void deveLerOsDocumentosDeUmZip() throws IOException {
        Path pacote = compactar("lote.zip", DocumentoDeTeste.ITEM_COMPLETO,
                DocumentoDeTeste.ITEM_COM_CAMPOS_AUSENTES, DocumentoDeTeste.NFCE_SEM_DESTINATARIO);

        assertThat(chavesLidasDe(pacote)).hasSize(3);
        assertThat(falhas.vazio()).isTrue();
    }

    @Test
    void deveRegistrarAFalhaESeguirOLoteQuandoUmaEntradaDoZipEIlegivel() throws IOException {
        Path pacote = compactar("lote.zip", DocumentoDeTeste.ITEM_COMPLETO, DocumentoDeTeste.CORROMPIDO);

        assertThat(chavesLidasDe(pacote)).hasSize(1);
        assertThat(falhas.falhas())
                .singleElement()
                .satisfies(falha -> assertThat(falha.origem())
                        .as("a origem precisa localizar a entrada dentro do pacote")
                        .contains("lote.zip")
                        .contains(DocumentoDeTeste.CORROMPIDO));
    }

    @Test
    void deveLiberarOZipAoFecharOFluxo() throws IOException {
        Path pacote = compactar("lote.zip", DocumentoDeTeste.ITEM_COMPLETO);

        chavesLidasDe(pacote);

        // No Windows, arquivo com descritor aberto não pode ser apagado: se a
        // exclusão funciona, o pacote foi mesmo fechado.
        assertThat(Files.deleteIfExists(pacote)).isTrue();
    }

    @Test
    void deveRecusarOrigemQueNaoEDiretorioNemZip() throws IOException {
        Path avulso = diretorio.resolve(DocumentoDeTeste.ITEM_COMPLETO);
        Files.copy(DocumentoDeTeste.caminho(DocumentoDeTeste.ITEM_COMPLETO), avulso);

        assertThatThrownBy(() -> leitorLote.ler(avulso))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".zip");
    }

    private List<String> chavesLidasDe(Path origem) throws IOException {
        try (Stream<DocumentoComItens> documentos = leitorLote.ler(origem)) {
            return documentos.map(DocumentoComItens::documento)
                    .map(Documento::chaveAcesso)
                    .map(chave -> chave.valor())
                    .toList();
        }
    }

    private void copiarParaODiretorio(String... nomes) throws IOException {
        for (String nome : nomes) {
            Files.copy(DocumentoDeTeste.caminho(nome), diretorio.resolve(nome));
        }
    }

    private Path compactar(String nomeDoPacote, String... nomes) throws IOException {
        Path pacote = diretorio.resolve(nomeDoPacote);
        try (OutputStream saida = Files.newOutputStream(pacote);
             ZipOutputStream compactado = new ZipOutputStream(saida, StandardCharsets.UTF_8)) {
            for (String nome : nomes) {
                compactado.putNextEntry(new ZipEntry(nome));
                Files.copy(DocumentoDeTeste.caminho(nome), compactado);
                compactado.closeEntry();
            }
        }
        return pacote;
    }
}
