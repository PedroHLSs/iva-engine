package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// D020 (04/10/2026): execução sem itens registrados — a do comando auditar — não tem contagem de produtos medida. Antes, o histórico gravava e mostrava quatro zeros, e o filtro minimoDeDivergencias=1 excluía uma execução com apontamentos. Agora a contagem sai "não registrado", e nenhum filtro de quantidade ou de situação exclui a execução, porque excluir por um valor que não foi medido esconde resultado real. A execução que leu zero itens continua com zeros: zero produtos ali é medição. Dados fictícios: NCM de oito zeros, código 999999, CSTs 999 e AAA, percentuais de dígitos repetidos, vigência desde 1900.
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
class HistoricoComContagemNaoMedidaTest {

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
    private ServicoDeAnalise analiseDaInterface;

    @Autowired
    private ServicoDeAuditoria auditoriaSemItensRegistrados;

    @Autowired
    private JdbcTemplate jdbc;

    private String semDivergencia;
    private String comDivergencia;
    private String naoMedida;
    private String semItens;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void quatroExecucoes() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);

        importacao.importar(carga("carga-normal", Set.of("999")));
        semDivergencia = analiseDaInterface.analisar(loteComANota()).id().toString();

        importacao.importar(carga("carga-cst-errado", Set.of("AAA")));
        comDivergencia = analiseDaInterface.analisar(loteComANota()).id().toString();
        // O caminho do comando auditar: grava a execução e os apontamentos, e nenhum item lido.
        naoMedida = auditoriaSemItensRegistrados.auditar(loteComANota()).execucao().id().toString();
        semItens = auditoriaSemItensRegistrados.auditar(Files.createTempDirectory("vazio-")).execucao().id().toString();
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    // O cenário demonstrado: a execução tem apontamentos, e o histórico não pode escrever zero onde não mediu.
    @Test
    void execucaoSemItensRegistradosDeveSairComContagemNaoRegistrada() throws IOException {
        assertThat(jdbc.queryForObject("select count(*) from achado_da_execucao where execucao_id = ?::uuid",
                Integer.class, naoMedida)).as("o cenário precisa ter apontamento").isPositive();

        JsonNode linha = linhaDe(historico(""), naoMedida);

        assertThat(linha.get("produtosPorSituacao").isNull()).as("contagem não medida não é zero").isTrue();
        assertThat(linha.get("produtosComAlgumaVerificacaoNaoConcluida").isNull()).isTrue();
        assertThat(linha.get("motivoDaContagemAusente").asText()).contains("não registrado");
        assertThat(linha.get("situacaoMaisGrave").isNull()).isTrue();
        assertThat(linha.get("motivoDaSituacaoAusente").asText()).contains("não registrado");
    }

    // O resumo gravado também não pode ser zero.
    @Test
    void oResumoGravadoNaoDeveTerZerosOndeNaoMediu() throws IOException {
        historico("");

        assertThat(jdbc.queryForObject(
                "select possivel_divergencia from resumo_da_execucao where execucao_id = ?::uuid",
                Integer.class, naoMedida)).isNull();
    }

    // Zero itens lidos é medição: zero produtos em cada estado, escrito.
    @Test
    void execucaoQueNaoLeuItemDeveContinuarComZeros() throws IOException {
        JsonNode linha = linhaDe(historico(""), semItens);

        assertThat(linha.get("produtosPorSituacao").isArray()).isTrue();
        for (JsonNode contagem : linha.get("produtosPorSituacao")) {
            assertThat(contagem.get("quantidade").asInt()).isZero();
        }
        assertThat(linha.get("motivoDaContagemAusente").isNull()).isTrue();
    }

    // O filtro de quantidade mínima não exclui a execução não medida: é o caso da execução de 15 apontamentos.
    @Test
    void oFiltroMinimoNaoDeveExcluirAExecucaoNaoMedida() throws IOException {
        JsonNode resposta = historico("minimoDeDivergencias=1");

        assertThat(ids(resposta)).containsExactlyInAnyOrder(comDivergencia, naoMedida);
        assertThat(resposta.get("execucoesNaoMedidasNoResultado").asLong()).isEqualTo(1);
    }

    @Test
    void oFiltroMaximoNaoDeveExcluirAExecucaoNaoMedida() throws IOException {
        assertThat(ids(historico("maximoDeDivergencias=0")))
                .containsExactlyInAnyOrder(semDivergencia, semItens, naoMedida);
    }

    // O filtro de situação também não exclui, e a resposta explica por que a execução está ali.
    @Test
    void oFiltroDeSituacaoNaoDeveExcluirAExecucaoNaoMedidaEDeveExplicar() throws IOException {
        JsonNode resposta = historico("situacaoMaisGrave=SEM_DIVERGENCIA_IDENTIFICADA");

        assertThat(ids(resposta)).containsExactlyInAnyOrder(semDivergencia, naoMedida);
        assertThat(ids(historico("situacaoMaisGrave=POSSIVEL_DIVERGENCIA")))
                .containsExactlyInAnyOrder(comDivergencia, naoMedida);
        assertThat(resposta.get("execucoesNaoMedidasNoResultado").asLong()).isEqualTo(1);
        assertThat(resposta.get("explicacaoDasNaoMedidas").asText())
                .contains("não há como classificá-las");
    }

    // Sem filtro, a contagem das não medidas também vem, para a tela não precisar adivinhar.
    @Test
    void semFiltroAsNaoMedidasTambemSaoContadas() throws IOException {
        assertThat(historico("").get("execucoesNaoMedidasNoResultado").asLong()).isEqualTo(1);
    }

    private JsonNode historico(String consulta) throws IOException {
        ResponseEntity<String> resposta = rest.getForEntity("/api/analises?" + consulta, String.class);
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(200);
        return new ObjectMapper().readTree(resposta.getBody());
    }

    private static List<String> ids(JsonNode resposta) {
        List<String> encontrados = new ArrayList<>();
        resposta.get("linhas").forEach(linha -> encontrados.add(linha.get("id").asText()));
        return encontrados;
    }

    private static JsonNode linhaDe(JsonNode resposta, String id) {
        for (JsonNode linha : resposta.get("linhas")) {
            if (linha.get("id").asText().equals(id)) {
                return linha;
            }
        }
        throw new AssertionError("a execução %s não está no histórico".formatted(id));
    }

    private Path loteComANota() throws IOException {
        Path pasta = Files.createTempDirectory("historico-nao-medido-");
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO)) {
            Files.write(pasta.resolve("nota.xml"), conteudo.readAllBytes());
        }
        return pasta;
    }

    private static CargaDeCatalogo carga(String versao, Set<String> cstsAdmitidos) {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("999999"),
                cstsAdmitidos.stream().map(CodigoCst::new).collect(Collectors.toSet()),
                "DISPOSITIVO FICTICIO PARA TESTE", false, Optional.of(BigDecimal.ZERO), Optional.empty(),
                Optional.empty(), Optional.of(List.of()), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        Abrangencia abrangencia = new Abrangencia("ABRANGENCIA-XX-FICTICIA");
        List<AliquotaVigente> aliquotas = List.of(
                new AliquotaVigente(Tributo.IBS_UF, new BigDecimal("9.9900"), abrangencia, procedencia),
                new AliquotaVigente(Tributo.IBS_MUN, new BigDecimal("8.8800"), abrangencia, procedencia),
                new AliquotaVigente(Tributo.CBS, new BigDecimal("7.7700"), abrangencia, procedencia));
        return new CargaDeCatalogo(versao, new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO, classificacoes, ncms, List.of(), aliquotas),
                classificacoes, ncms, List.of(), aliquotas);
    }
}
