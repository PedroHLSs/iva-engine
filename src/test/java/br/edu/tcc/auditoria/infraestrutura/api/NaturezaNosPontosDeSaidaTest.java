package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ServicoDeExportacao;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
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

// D021 (04/10/2026): a natureza do catálogo viaja até todo lugar onde um resultado é exibido ou exportado. O cenário é o demonstrado: tabelas normativas e a cobertura fictícia — antes, a cobertura escapava da natureza, a faixa dizia "Catálogo normativo", a visão técnica e o histórico não traziam natureza nenhuma, e a planilha não falava em fictício. Dados fictícios: NCM de oito zeros, código 999999, CST 999, percentuais de dígitos repetidos, vigência desde 1900; NORMATIVO aqui só exercita a declaração.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "spring.jackson.default-property-inclusion=always",
                "auditoria.tolerancia-de-valor=0.01",
                "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
        })
@Testcontainers
@EnabledIf("dockerDisponivel")
class NaturezaNosPontosDeSaidaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO = "/documentos/nfe-item-completo.xml";
    private static final String MISTO = "PARCIALMENTE_FICTICIO";

    private static final String TABELAS = String.join(", ",
            "correcao_de_analise", "autoria_da_execucao", "resumo_da_execucao",
            "achado_evidencia", "achado_da_execucao", "achado", "tratativa",
            "avaliacao_nao_concluida", "item_da_execucao", "falha_de_leitura_da_execucao",
            "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "natureza_da_carga", "cobertura_catalogo", "carga_catalogo", "usuario");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ServicoDeUsuarios usuarios;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacao;

    @Autowired
    private ServicoDeAnalise analise;

    @Autowired
    private ServicoDeExportacao exportacao;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path pasta;

    private String execucao;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void umaAnaliseContraCatalogoComCoberturaFicticia() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        importacao.importar(cargaComCoberturaFicticia());
        Path lote = Files.createDirectories(pasta.resolve("lote"));
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO)) {
            Files.write(lote.resolve("nota.xml"), conteudo.readAllBytes());
        }
        execucao = analise.analisar(lote).id().toString();
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    // A cobertura entra na natureza gravada.
    @Test
    void aNaturezaDaCoberturaDeveSerGravada() {
        assertThat(jdbc.queryForObject(
                "select natureza from natureza_da_carga where tabela = 'COBERTURA'", String.class))
                .isEqualTo("FICTICIO");
    }

    // A conferência deixa de dizer "Catálogo normativo".
    @Test
    void aConferenciaDeveDizerParcialmenteFicticioComACobertura() throws IOException {
        assertFaixaMista(get("/api/analises/" + execucao).get("natureza"));
    }

    // A visão técnica: a execução, os apontamentos, os não avaliados e a lista.
    @Test
    void aVisaoTecnicaDeveTrazerANaturezaEmTodaResposta() throws IOException {
        assertFaixaMista(get("/api/execucoes/" + execucao).get("natureza"));
        assertFaixaMista(get("/api/execucoes/" + execucao + "/achados").get("natureza"));
        assertFaixaMista(get("/api/execucoes/" + execucao + "/nao-avaliados").get("natureza"));
        assertFaixaMista(get("/api/execucoes").get("execucoes").get(0).get("natureza"));
    }

    @Test
    void oHistoricoDeveTrazerANaturezaEmCadaLinha() throws IOException {
        assertFaixaMista(get("/api/analises").get("linhas").get(0).get("natureza"));
    }

    // A planilha da mesma execução diz que o catálogo é parcialmente fictício e qual tabela é.
    @Test
    void aPlanilhaDeveDizerQueACoberturaEFicticia() throws IOException {
        Path destino = pasta.resolve("papel.xlsx");
        exportacao.exportar(java.util.UUID.fromString(execucao), destino);

        try (Workbook planilha = new XSSFWorkbook(Files.newInputStream(destino))) {
            for (Sheet aba : planilha) {
                assertThat(aba.getRow(0).getCell(0).getStringCellValue())
                        .as("faixa da aba %s", aba.getSheetName())
                        .startsWith("CATÁLOGO PARCIALMENTE FICTÍCIO")
                        .contains("COBERTURA");
            }
        }
    }

    private static void assertFaixaMista(JsonNode natureza) {
        assertThat(natureza).as("faixa de natureza na resposta").isNotNull();
        assertThat(natureza.get("situacao").asText()).isEqualTo(MISTO);
        assertThat(natureza.get("exigeAviso").asBoolean()).isTrue();
        List<String> ficticias = new java.util.ArrayList<>();
        natureza.get("tabelasFicticias").forEach(tabela -> ficticias.add(tabela.asText()));
        assertThat(ficticias).containsExactly(NaturezaDaCarga.COBERTURA);
    }

    private JsonNode get(String caminho) throws IOException {
        ResponseEntity<String> resposta = rest.getForEntity(caminho, String.class);
        assertThat(resposta.getStatusCode().value()).as(caminho + " " + resposta.getBody()).isEqualTo(200);
        return new ObjectMapper().readTree(resposta.getBody());
    }

    private static CargaDeCatalogo cargaComCoberturaFicticia() {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("999999"), Set.of(new CodigoCst("999")),
                "DISPOSITIVO FICTICIO PARA TESTE", false, Optional.of(BigDecimal.ZERO), Optional.empty(),
                Optional.empty(), Optional.of(List.of()), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        Abrangencia abrangencia = new Abrangencia("ABRANGENCIA-XX-FICTICIA");
        List<AliquotaVigente> aliquotas = List.of(
                new AliquotaVigente(Tributo.IBS_UF, new BigDecimal("9.9900"), abrangencia, procedencia),
                new AliquotaVigente(Tributo.IBS_MUN, new BigDecimal("8.8800"), abrangencia, procedencia),
                new AliquotaVigente(Tributo.CBS, new BigDecimal("7.7700"), abrangencia, procedencia));
        Optional<Natureza> normativo = Optional.of(Natureza.NORMATIVO);
        NaturezaDaCarga natureza = new NaturezaDaCarga(normativo, normativo, Optional.empty(), normativo,
                Optional.empty(), Optional.of(Natureza.FICTICIO));
        return new CargaDeCatalogo("carga-cobertura-ficticia", new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                natureza, classificacoes, ncms, List.of(), aliquotas);
    }
}
