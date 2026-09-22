package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ServicoDeExportacao;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Do XML à planilha, contra um PostgreSQL de verdade.
 *
 * <p>Prova o critério da etapa: um achado exportado permite chegar ao documento,
 * ao campo e ao dispositivo legal <strong>sem consultar o banco</strong>. Os
 * testes de unidade cobrem cada peça; este cobre a costura entre elas, que é
 * onde o papel de trabalho pode sair certo em cada parte e errado no todo.</p>
 *
 * <p>Sem Docker, desabilita em vez de falhar — mesma escolha de
 * {@code PersistenciaDeAuditoriaTest}.</p>
 */
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class PapelDeTrabalhoDePontaAPontaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    /** A carga destes testes é inteiramente de demonstração, e a faixa diz isso. */
    private static final NaturezaDaCarga NATUREZA_FICTICIA = new NaturezaDaCarga(
            java.util.Optional.of(Natureza.FICTICIO),
            java.util.Optional.of(Natureza.FICTICIO),
            java.util.Optional.empty(),
            java.util.Optional.empty());
    private static final String DOCUMENTO_DE_TESTE = "/documentos/nfe-item-completo.xml";
    private static final String CHAVE_DO_DOCUMENTO = "1".repeat(44);
    private static final String FONTE_FICTICIA = "FONTE FICTICIA PARA TESTE v0.0";
    private static final String CODIGO_FICTICIO = "999999";
    private static final String CST_FICTICIO_ADMITIDO = "AAA";
    private static final String NCM_FICTICIO = "00000000";
    private static final String DISPOSITIVO_FICTICIO = "Dispositivo ficticio para teste";
    private static final String JUSTIFICATIVA = "Justificativa ficticia de teste.";

    private static final String TABELAS = String.join(", ",
            "achado_evidencia", "achado_da_execucao", "achado", "tratativa",
            "avaliacao_nao_concluida", "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "cobertura_catalogo", "carga_catalogo");

    @TempDir
    private Path pasta;

    private Path lote;
    private Path planilha;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacaoDeCatalogo;

    @Autowired
    private ServicoDeAuditoria auditoria;

    @Autowired
    private ServicoDeExportacao exportacao;

    @Autowired
    private ServicoDeTratativa tratativas;

    @Autowired
    private ConsultaDeAchados consulta;

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
    void prepararBancoELote() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(catalogoFicticio());

        lote = Files.createDirectories(pasta.resolve("lote"));
        planilha = pasta.resolve("papel-de-trabalho.xlsx");
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO_DE_TESTE)) {
            if (conteudo == null) {
                throw new IllegalStateException(
                        "Documento de teste não encontrado no classpath: " + DOCUMENTO_DE_TESTE);
            }
            Files.copy(conteudo, lote.resolve("documento.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
        auditoria.auditar(lote);
    }

    @Test
    void deveEmitirAPlanilhaDaUltimaExecucaoComAsTresAbas() throws IOException {
        PapelDeTrabalho papel = exportacao.exportarUltima(planilha);

        assertThat(planilha).exists();
        try (Workbook gerada = abrir(planilha)) {
            assertThat(gerada.getSheetName(0)).isEqualTo(ExportadorXlsx.ABA_RESUMO);
            assertThat(gerada.getSheetName(1)).isEqualTo(ExportadorXlsx.ABA_ACHADOS);
            assertThat(gerada.getSheetName(2)).isEqualTo(ExportadorXlsx.ABA_NAO_AVALIADOS);
        }
        assertThat(papel.achados()).hasSize(1);
    }

    @Test
    void deveTrazerNaLinhaDoAchadoOQuePermiteConferirSemAbrirOSistema() throws IOException {
        exportacao.exportarUltima(planilha);

        try (Workbook gerada = abrir(planilha)) {
            Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);
            assertThat(achados.getLastRowNum()).isEqualTo(1);

            assertThat(achados.getRow(1).getCell(0).getStringCellValue())
                    .as("documento pseudonimizado, nunca a chave")
                    .hasSize(64);
            assertThat(achados.getRow(1).getCell(4).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(achados.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(1);
            assertThat(achados.getRow(1).getCell(13).getStringCellValue())
                    .as("o dispositivo legal vem do catálogo importado e chega inteiro à planilha")
                    .isEqualTo(DISPOSITIVO_FICTICIO);
            assertThat(achados.getRow(1).getCell(17).getStringCellValue()).isEqualTo("ABERTO");
        }
    }

    @Test
    void deveListarOsNaoAvaliadosComOMotivoDaRegraQueNaoConcluiu() throws IOException {
        PapelDeTrabalho papel = exportacao.exportarUltima(planilha);

        assertThat(papel.quantidadeDeNaoAvaliados())
                .as("o catálogo fictício não traz alíquota, então a regra de valor não conclui")
                .isPositive();

        try (Workbook gerada = abrir(planilha)) {
            Sheet naoAvaliados = gerada.getSheet(ExportadorXlsx.ABA_NAO_AVALIADOS);
            assertThat(naoAvaliados.getLastRowNum()).isEqualTo(papel.quantidadeDeNaoAvaliados());
            assertThat(naoAvaliados.getRow(1).getCell(7).getStringCellValue())
                    .as("o motivo diz o que faltou, e não apenas que faltou algo")
                    .isNotBlank();
        }
    }

    @Test
    void naoDeveEscreverAChaveDeAcessoNaPlanilha() throws IOException {
        exportacao.exportarUltima(planilha);

        assertThat(conteudoDescompactado(planilha))
                .as("a chave carrega o CNPJ do emitente e não sai na exportação")
                .doesNotContain(CHAVE_DO_DOCUMENTO);
    }

    @Test
    void deveMostrarATratativaRegistradaDepoisDaAuditoria() throws IOException {
        AchadoRegistrado registrado = consulta.listar(FiltroDeAchados.tudo()).get(0);
        tratativas.registrar(registrado.id(), DecisaoDeTratativa.ACEITO, JUSTIFICATIVA);

        exportacao.exportarUltima(planilha);

        try (Workbook gerada = abrir(planilha)) {
            Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);
            assertThat(achados.getRow(1).getCell(17).getStringCellValue()).isEqualTo("ACEITO");
            assertThat(achados.getRow(1).getCell(18).getStringCellValue()).isEqualTo(JUSTIFICATIVA);
        }
    }

    @Test
    void deveReemitirOPapelDeTrabalhoDeUmaExecucaoAnterior() throws IOException {
        PapelDeTrabalho daPrimeira = exportacao.exportarUltima(planilha);

        ResultadoDaAuditoria segunda = auditoria.auditar(lote);
        Path daSegunda = pasta.resolve("segunda.xlsx");
        exportacao.exportar(segunda.execucao().id(), daSegunda);

        Path reemitida = pasta.resolve("reemitida.xlsx");
        PapelDeTrabalho reemissao = exportacao.exportar(daPrimeira.execucao().id(), reemitida);

        assertThat(reemissao.execucao().id()).isEqualTo(daPrimeira.execucao().id());
        assertThat(reemissao.achados())
                .as("a rodada antiga continua tendo os apontamentos que ela produziu, mesmo depois "
                        + "de uma rodada nova reencontrá-los")
                .hasSameSizeAs(daPrimeira.achados());
        assertThat(reemissao.quantidadeDeNaoAvaliados())
                .isEqualTo(daPrimeira.quantidadeDeNaoAvaliados());
    }

    private static Workbook abrir(Path caminho) throws IOException {
        try (InputStream conteudo = Files.newInputStream(caminho)) {
            return WorkbookFactory.create(conteudo);
        }
    }

    private static String conteudoDescompactado(Path caminho) throws IOException {
        StringBuilder conteudo = new StringBuilder();
        try (ZipFile zip = new ZipFile(caminho.toFile())) {
            Enumeration<? extends ZipEntry> entradas = zip.entries();
            while (entradas.hasMoreElements()) {
                try (InputStream parte = zip.getInputStream(entradas.nextElement())) {
                    conteudo.append(new String(parte.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return conteudo.toString();
    }

    /** Catálogo fictício que reconhece o código e o NCM, mas admite outro CST. */
    private static CargaDeCatalogo catalogoFicticio() {
        ProcedenciaNormativa procedencia =
                ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), FONTE_FICTICIA);

        return new CargaDeCatalogo(
                "carga-ficticia",
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NATUREZA_FICTICIA,
                List.of(new ClassificacaoTributaria(
                        new CodigoClassificacaoTributaria(CODIGO_FICTICIO),
                        Set.of(new CodigoCst(CST_FICTICIO_ADMITIDO)),
                        DISPOSITIVO_FICTICIO,
                        false,
                        Optional.empty(),
                        List.of(),
                        procedencia)),
                List.of(new RegistroNcm(
                        new Ncm(NCM_FICTICIO), "Descricao ficticia de teste", procedencia)),
                List.of(),
                List.of());
    }
}
