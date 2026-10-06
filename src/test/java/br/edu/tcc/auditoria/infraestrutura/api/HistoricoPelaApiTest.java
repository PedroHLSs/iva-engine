package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ResultadoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O histórico de análises, paginado e filtrado no servidor.
 *
 * <p>Três análises reais da mesma nota, contra três cargas diferentes, em ordem:
 * uma sem divergência, disparada por um fiscal; uma com verificação não concluída,
 * disparada por um administrador; e uma com possível divergência, disparada sem
 * pessoa logada — o caminho da linha de comando —, que fica com "executor não
 * registrado". Cada filtro e a paginação são conferidos pela resposta do servidor.</p>
 *
 * <p>Dados fictícios: NCM de oito zeros, código {@code 999999}, CST {@code 999},
 * percentuais de dígitos repetidos, vigência a partir de 1900.</p>
 */
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
class HistoricoPelaApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO = "/documentos/nfe-item-completo.xml";

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
    private ServicoDeAnalise servicoDeAnalise;

    @Autowired
    private JdbcTemplate jdbc;

    private String semDivergencia;
    private String naoConcluida;
    private String comDivergencia;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void tresAnalises() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");

        importacao.importar(carga("carga-normal", Set.of("999"), aliquotas()));
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);
        semDivergencia = enviar();

        importacao.importar(carga("carga-sem-aliquota", Set.of("999"), List.of()));
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        naoConcluida = enviar();

        importacao.importar(carga("carga-cst-errado", Set.of("AAA"), aliquotas()));
        comDivergencia = analisarSemPessoaLogada();
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void aOrdemPadraoDeveSerDaMaisRecenteParaAMaisAntiga() {
        String corpo = historico("");

        assertThat(ids(corpo)).containsExactly(comDivergencia, naoConcluida, semDivergencia);
        assertThat(corpo).contains("\"ordem\":\"da mais recente para a mais antiga\"");
    }

    @Test
    void aIdentificacaoDaExecucaoDeveEstarNaListagemSemCliqueNenhum() {
        String corpo = historico("");

        assertThat(corpo)
                .contains("\"versaoDoCatalogo\":\"carga-normal\"")
                .contains("\"versaoDoCatalogo\":\"carga-sem-aliquota\"")
                .contains("\"versaoDasRegras\":\"2026.6\"");
    }

    @Test
    void cadaLinhaDeveTrazerOsQuatroEstadosInclusiveOsZeros() {
        String linha = linhaDe(historico(""), naoConcluida);

        assertThat(quantidade(linha, "NAO_FOI_POSSIVEL_CONCLUIR")).isEqualTo(1);
        assertThat(quantidade(linha, "SEM_DIVERGENCIA_IDENTIFICADA")).isZero();
        assertThat(quantidade(linha, "POSSIVEL_DIVERGENCIA")).isZero();
        assertThat(quantidade(linha, "REQUER_CONFERENCIA")).isZero();
    }

    @Test
    void aPaginacaoDeveAcontecerNoServidor() {
        String segunda = historico("pagina=1&tamanho=1");

        assertThat(ids(segunda)).containsExactly(naoConcluida);
        assertThat(segunda)
                .contains("\"totalDeLinhas\":3")
                .contains("\"totalDePaginas\":3");
    }

    @Test
    void oFiltroDeSituacaoDeveSerPelaMaisGravePresente() {
        assertThat(ids(historico("situacaoMaisGrave=NAO_FOI_POSSIVEL_CONCLUIR"))).containsExactly(naoConcluida);
        assertThat(ids(historico("situacaoMaisGrave=SEM_DIVERGENCIA_IDENTIFICADA"))).containsExactly(semDivergencia);
        assertThat(ids(historico("situacaoMaisGrave=POSSIVEL_DIVERGENCIA"))).containsExactly(comDivergencia);
        assertThat(historico(""))
                .as("o rótulo do filtro diz o critério, e não só \"situação\"")
                .contains("\"rotuloDoFiltroDeSituacao\":\"Situação mais grave presente\"")
                .contains("e não pela da maioria");
    }

    @Test
    void oFiltroDeQuantidadeDeDivergenciasDeveAcontecerNoServidor() {
        assertThat(ids(historico("minimoDeDivergencias=1"))).containsExactly(comDivergencia);
        assertThat(ids(historico("maximoDeDivergencias=0"))).containsExactly(naoConcluida, semDivergencia);
    }

    @Test
    void oFiltroDeExecutorDeveAcontecerNoServidor() {
        assertThat(ids(historico("executor=teste.fiscal"))).containsExactly(semDivergencia);
        assertThat(ids(historico("executor=teste.administrador"))).containsExactly(naoConcluida);
    }

    @Test
    void execucaoSemPessoaLogadaDeveFicarComExecutorNaoRegistradoEscrito() {
        String corpo = historico("semExecutorRegistrado=true");

        assertThat(ids(corpo)).containsExactly(comDivergencia);
        assertThat(corpo)
                .contains("\"executor\":null")
                .contains("executor não registrado");
    }

    @Test
    void oFiltroDePeriodoDeveAcontecerNoServidor() {
        jdbc.update("update execucao_auditoria set data_hora = '2020-01-10 12:00:00+00' where id = ?::uuid",
                semDivergencia);

        assertThat(ids(historico("ate=2020-01-31"))).containsExactly(semDivergencia);
        assertThat(ids(historico("de=2021-01-01"))).containsExactly(comDivergencia, naoConcluida);
    }

    @Test
    void osFiltrosDevemSeCombinar() {
        assertThat(ids(historico("maximoDeDivergencias=0&executor=teste.administrador")))
                .containsExactly(naoConcluida);
    }

    @Test
    void filtroIncoerenteDeveSerRecusadoComAMensagemDoQueCorrigir() {
        ResponseEntity<String> resposta = rest.getForEntity(
                "/api/analises?minimoDeDivergencias=5&maximoDeDivergencias=1", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("FILTRO_INVALIDO").contains("maior que o máximo");
    }

    @Test
    void oResumoGravadoDeveBaterComAConferenciaDaAnalise() {
        String conferencia = rest.getForEntity("/api/analises/" + naoConcluida, String.class).getBody();
        historico("");

        Integer naoConcluidos = jdbc.queryForObject(
                "select nao_foi_possivel_concluir from resumo_da_execucao where execucao_id = ?::uuid",
                Integer.class, naoConcluida);
        assertThat(conferencia).contains("NAO_FOI_POSSIVEL_CONCLUIR");
        assertThat(naoConcluidos).isEqualTo(1);
    }

    private String historico(String consulta) {
        ResponseEntity<String> resposta = rest.getForEntity("/api/analises?" + consulta, String.class);
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(200);
        return resposta.getBody();
    }

    private static List<String> ids(String corpo) {
        List<String> encontrados = new ArrayList<>();
        Matcher linha = Pattern.compile("\\{\"id\":\"([0-9a-f-]{36})\",\"dataHora\"").matcher(corpo);
        while (linha.find()) {
            encontrados.add(linha.group(1));
        }
        return encontrados;
    }

    private static String linhaDe(String corpo, String id) {
        int inicio = corpo.indexOf("{\"id\":\"" + id + "\"");
        assertThat(inicio).as("linha %s no histórico", id).isNotNegative();
        int fim = corpo.indexOf("\"motivoDaSituacaoAusente\"", inicio);
        return corpo.substring(inicio, fim);
    }

    private static int quantidade(String linha, String estado) {
        Matcher achado = Pattern.compile("\"estado\":\"" + estado + "\"[^}]*?\"quantidade\":(\\d+)").matcher(linha);
        assertThat(achado.find()).as("estado %s na linha", estado).isTrue();
        return Integer.parseInt(achado.group(1));
    }

    private String enviar() throws IOException {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        byte[] conteudo = documento();
        corpo.add(ControladorDeAnalises.CAMPO_DO_ARQUIVO, new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return "nota.xml";
            }
        });
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> resposta =
                rest.postForEntity("/api/analises", new HttpEntity<>(corpo, cabecalhos), String.class);
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(201);
        Matcher id = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(resposta.getBody());
        assertThat(id.find()).isTrue();
        return id.group(1);
    }

    /** O mesmo caminho do comando auditar e de análise anterior à Etapa 13: sem pessoa logada, sem executor. */
    private String analisarSemPessoaLogada() throws IOException {
        Path pasta = Files.createTempDirectory("historico-");
        Files.write(pasta.resolve("nota.xml"), documento());
        ResultadoDaAnalise resultado = servicoDeAnalise.analisar(pasta);
        return resultado.id().toString();
    }

    private byte[] documento() throws IOException {
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO)) {
            return conteudo.readAllBytes();
        }
    }

    private static CargaDeCatalogo carga(String versao, Set<String> cstsAdmitidos, List<AliquotaVigente> aliquotas) {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("999999"),
                cstsAdmitidos.stream().map(CodigoCst::new).collect(java.util.stream.Collectors.toSet()),
                "DISPOSITIVO FICTICIO PARA TESTE", false, Optional.of(BigDecimal.ZERO), Optional.empty(),
                // D015: NENHUM declarado. Lista vazia passou a ser "não declarado", e a R07 não concluiria.
                Optional.empty(), Optional.of(List.of()), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        return new CargaDeCatalogo(versao, new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO, classificacoes, ncms, List.of(), aliquotas),
                classificacoes, ncms, List.of(), aliquotas);
    }

    private static List<AliquotaVigente> aliquotas() {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        Abrangencia abrangencia = new Abrangencia("ABRANGENCIA-XX-FICTICIA");
        return List.of(
                new AliquotaVigente(Tributo.IBS_UF, new BigDecimal("9.9900"), abrangencia, procedencia),
                new AliquotaVigente(Tributo.IBS_MUN, new BigDecimal("8.8800"), abrangencia, procedencia),
                new AliquotaVigente(Tributo.CBS, new BigDecimal("7.7700"), abrangencia, procedencia));
    }
}
