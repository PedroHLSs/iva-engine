package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D015 (03/10/2026): os seis campos do grupo gRed atravessam a gravação (V18) e voltam com a escala que a nota declarou. Usa a nota fictícia nfe-item-completo-reducao-60.xml, de chave com 44 dígitos repetidos, e catálogo fictício.
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class ReducaoDeAliquotaNoBancoTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String NOTA = "/documentos/nfe-item-completo-reducao-60.xml";
    private static final String CHAVE = "1".repeat(44);

    @TempDir
    private Path lote;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacao;

    @Autowired
    private ServicoDeAuditoria auditoria;

    @Autowired
    private ItemDocumentoJpa itens;

    @Autowired
    private JdbcTemplate jdbc;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void auditarANota() throws IOException {
        jdbc.execute("truncate table carga_catalogo, documento, item_documento, execucao_auditoria cascade");
        importacao.importar(carga());
        try (InputStream nota = getClass().getResourceAsStream(NOTA)) {
            Files.copy(nota, lote.resolve("nota.xml"));
        }
        auditoria.auditar(lote);
    }

    @Test
    void osSeisCamposDevemVoltarComAEscalaDeclarada() {
        ItemDocumento lido = MapeadorDeDocumento.paraDominio(
                itens.findByChaveAcessoAndNumeroItem(CHAVE, 1).orElseThrow());

        assertThat(lido.reducaoAliquotaIbsUf()).contains(new BigDecimal("60.0000"));
        assertThat(lido.aliquotaEfetivaIbsUf()).contains(new BigDecimal("3.9960"));
        assertThat(lido.reducaoAliquotaIbsMunicipal()).contains(new BigDecimal("60.0000"));
        assertThat(lido.aliquotaEfetivaIbsMunicipal()).contains(new BigDecimal("3.5520"));
        assertThat(lido.reducaoAliquotaCbs()).contains(new BigDecimal("60.0000"));
        assertThat(lido.aliquotaEfetivaCbs()).contains(new BigDecimal("3.1080"));
    }

    // Item gravado antes da V18 tem as seis colunas nulas, e elas voltam como ausência, nunca como zero.
    @Test
    void colunaNulaDeveVoltarComoAusencia() {
        jdbc.update("update item_documento set reducao_aliquota_cbs = null, aliquota_efetiva_cbs = null");

        ItemDocumento lido = MapeadorDeDocumento.paraDominio(
                itens.findByChaveAcessoAndNumeroItem(CHAVE, 1).orElseThrow());

        assertThat(lido.reducaoAliquotaCbs()).isEmpty();
        assertThat(lido.aliquotaEfetivaCbs()).isEmpty();
    }

    // Método auxiliar que monta uma carga fictícia mínima; o conteúdo dela não importa para o que este teste confere.
    private static CargaDeCatalogo carga() {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("999999"), Set.of(new CodigoCst("AAA")),
                "DISPOSITIVO FICTICIO PARA TESTE", false, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(List.of()), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        return new CargaDeCatalogo("carga-ficticia-gred", new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO, classificacoes, ncms, List.of(), List.of()),
                classificacoes, ncms, List.of(), List.of());
    }
}
