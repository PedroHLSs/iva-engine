package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;

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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// D026 (04/10/2026): a importação parcial pelo POST /api/cargas, contra PostgreSQL de verdade. A tela lê a carga mais recente no GET /api/cargas e devolve no pedido a versão e os dois instantes exatamente como vieram; este teste faz o mesmo, e com isso prova que os instantes atravessam o JSON sem perder precisão. Valores fictícios: cClassTrib XXX000, CST AAA, NCM 00000000, vigência em 1900.
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
class ImportacaoParcialPelaApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ServicoDeUsuarios usuarios;

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
    void prepararBanco() {
        jdbc.execute("truncate table carga_catalogo cascade");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void umArquivoComAcervoVazioDeveSerRecusadoComAMensagemDeHoje() throws IOException {
        ResponseEntity<String> resposta = importar("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                null);

        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(corpo.get("erro").asText()).isEqualTo("CARGA_RECUSADA");
        assertThat(resposta.getBody()).contains("Falta o arquivo \\\"classificacao-tributaria.csv\\\"");
        assertThat(contarCargas()).isZero();
    }

    @Test
    void parcialComOrigemCorretaDeveCriarAVersaoDigitadaComDerivadaDe() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);

        ResponseEntity<String> resposta = importar("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                vista("carga-a"));

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(201);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("tipo").asText()).isEqualTo("PARCIAL");
        assertThat(corpo.get("versao").asText()).isEqualTo("carga-b");
        assertThat(jdbc.queryForObject("""
                select origem.versao from carga_catalogo c join carga_catalogo origem on origem.id = c.derivada_de
                 where c.versao = 'carga-b'""", String.class)).isEqualTo("carga-a");
    }

    @Test
    void osInstantesDoGetDevemAtravessarOJsonSemPerderPrecisao() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);

        Instant pelaApi = Instant.parse(vista("carga-a").get("importadoEm").asText());
        Instant noBanco = jdbc.queryForObject("select importado_em from carga_catalogo where versao = 'carga-a'",
                Timestamp.class).toInstant();

        assertThat(pelaApi).isEqualTo(noBanco);
        assertThat(importar("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vista("carga-a"))
                .getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void asTabelasHerdadasDevemSerIdenticasAsDaOrigemComANaturezaDaOrigem() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);

        importar("carga-b", Map.of("aliquota-vigente.csv", aliquotas("NORMATIVO")), vista("carga-a"));

        for (String tabela : List.of("classificacao_tributaria", "registro_ncm", "item_anexo", "cobertura_catalogo",
                "anexo_declarado")) {
            assertThat(conteudo(tabela, "carga-b")).as(tabela).isEqualTo(conteudo(tabela, "carga-a"));
        }
        assertThat(naturezas("carga-b")).containsExactly("ALIQUOTA=NORMATIVO", "CLASSIFICACAO_TRIBUTARIA=FICTICIO",
                "COBERTURA=FICTICIO", "ITEM_ANEXO=FICTICIO", "NCM=FICTICIO");
        JsonNode carga = JSON.readTree(rest.getForEntity("/api/cargas/carga-b", String.class).getBody());
        assertThat(carga.get("natureza").toString()).contains("PARCIALMENTE_FICTICIO");
    }

    @Test
    void aOrigemRascunhoDeveContinuarIntactaDepoisDaParcial() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);
        String antes = impressaoDigital("carga-a");

        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("classificacao-tributaria.csv", classificacao("Dispositivo ficticio B", "NENHUM"));
        arquivos.put("cobertura.csv", catalogo("x").get("cobertura.csv"));
        ResponseEntity<String> resposta = importar("carga-b", arquivos, vista("carga-a"));

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(201);
        assertThat(impressaoDigital("carga-a")).isEqualTo(antes);
        assertThat(jdbc.queryForObject("select alterada_em is null and selada_em is null from carga_catalogo "
                + "where versao = 'carga-a'", Boolean.class)).isTrue();
    }

    @Test
    void origemQueMudouEntreATelaEOEnvioDeveDar409SemGravarComAOrigemAtual() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);
        JsonNode vistaDaA = vista("carga-a");
        importar("carga-c", catalogo("Dispositivo ficticio C"), null);

        ResponseEntity<String> resposta = importar("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                vistaDaA);

        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(409);
        assertThat(corpo.get("erro").asText()).isEqualTo("ORIGEM_DESATUALIZADA");
        assertThat(corpo.get("origemAtual").get("versao").asText()).isEqualTo("carga-c");
        assertThat(corpo.get("origemAtual").get("importadoEm").asText())
                .isEqualTo(vista("carga-c").get("importadoEm").asText());
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void parcialSemOrigemDeveDar400ComAOrigemAtualSemGravar() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);

        ResponseEntity<String> resposta = importar("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                null);

        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(corpo.get("erro").asText()).isEqualTo("ORIGEM_NAO_INFORMADA");
        assertThat(corpo.get("origemAtual").get("versao").asText()).isEqualTo("carga-a");
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void parcialQueViolaAGuardaDeCoberturaDeveSerRecusadaInteira() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("registro-ncm.csv", "ncm;descricao;" + DADOS + "\n");
        arquivos.put("cobertura.csv", catalogo("x").get("cobertura.csv"));

        ResponseEntity<String> resposta = importar("carga-b", arquivos, vista("carga-a"));

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("cobertura sobre tabela sem nenhum registro");
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void parcialComAnexoHerdadoQueNaoDeclaraOCodigoDeveSerRecusada() throws IOException {
        Map<String, String> comAnexos = catalogo("Dispositivo ficticio A");
        comAnexos.put("anexos-declarados.csv", """
                identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                """);
        assertThat(importar("carga-a", comAnexos, null).getStatusCode().value()).isEqualTo(201);
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("classificacao-tributaria.csv", classificacao("Dispositivo ficticio B", "ANEXO-ZZ"));
        arquivos.put("cobertura.csv", comAnexos.get("cobertura.csv"));

        ResponseEntity<String> resposta = importar("carga-b", arquivos, vista("carga-a"));

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("ANEXO-ZZ");
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void cincoArquivosComAcervoPovoadoDevemImportarSemHeranca() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);

        ResponseEntity<String> resposta = importar("carga-b", catalogo("Dispositivo ficticio B"), vista("carga-a"));

        JsonNode corpo = JSON.readTree(resposta.getBody());
        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(201);
        assertThat(corpo.get("tipo").asText()).isEqualTo("COMPLETA");
        assertThat(corpo.get("tabelasHerdadas")).isEmpty();
        assertThat(corpo.get("cargaDeOrigem").isNull()).isTrue();
        assertThat(corpo.get("motivoDaOrigemAusente").asText()).contains("nenhuma tabela herdada");
        assertThat(corpo.get("origemInformadaNaoUsada").asText()).isEqualTo("carga-a");
        assertThat(jdbc.queryForObject("select derivada_de is null from carga_catalogo where versao = 'carga-b'",
                Boolean.class)).isTrue();
    }

    @Test
    void aRespostaDeveDizerQuaisTabelasForamHerdadasEDeQualCarga() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);

        JsonNode corpo = JSON.readTree(importar("carga-b",
                Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vista("carga-a")).getBody());

        assertThat(corpo.get("cargaDeOrigem").asText()).isEqualTo("carga-a");
        assertThat(lista(corpo.get("tabelasEnviadas"))).containsExactly("ALIQUOTA");
        assertThat(lista(corpo.get("tabelasHerdadas"))).containsExactly("CLASSIFICACAO_TRIBUTARIA", "NCM",
                "ITEM_ANEXO", "COBERTURA", "ANEXO_DECLARADO");
        assertThat(corpo.get("aviso").asText()).contains("vieram da carga \"carga-a\"", "não mudou");
        assertThat(corpo.get("origemImportadaEm").asText()).isEqualTo(vista("carga-a").get("importadoEm").asText());
    }

    @Test
    void versaoDigitadaQueJaExisteDeveSerRecusada() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);
        String antes = impressaoDigital("carga-a");

        ResponseEntity<String> resposta = importar("carga-a", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                vista("carga-a"));

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("Já existe carga de catálogo com a versão");
        assertThat(impressaoDigital("carga-a")).isEqualTo(antes);
    }

    @Test
    void oEditarDeveExigirCoberturaJuntoComTabelaComCobertura() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);
        String antes = impressaoDigital("carga-a");

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("efeitoEsperado", "ALTERAR_RASCUNHO");
        corpo.add("arquivos", arquivo("classificacao-tributaria.csv", classificacao("Dispositivo ficticio B", "NENHUM")));
        ResponseEntity<String> resposta = rest.exchange("/api/cargas/carga-a", HttpMethod.PUT,
                new HttpEntity<>(corpo, multipart()), String.class);

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("exige cobertura.csv junto");
        assertThat(impressaoDigital("carga-a")).isEqualTo(antes);
    }

    @Test
    void instanteQueNaoEhInstanteDeveSerRecusadoSemGravar() throws IOException {
        importar("carga-a", catalogo("Dispositivo ficticio A"), null);
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("versao", "carga-b");
        corpo.add("cargaDeOrigemEsperada", "carga-a");
        corpo.add("origemImportadaEmEsperada", "ontem");
        corpo.add("arquivos", arquivo("aliquota-vigente.csv", aliquotas("FICTICIO")));

        ResponseEntity<String> resposta = rest.postForEntity("/api/cargas", new HttpEntity<>(corpo, multipart()),
                String.class);

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("origemImportadaEmEsperada");
        assertThat(existe("carga-b")).isFalse();
    }

    // Lê no GET /api/cargas a carga, como a tela lê.
    private JsonNode vista(String versao) throws IOException {
        JsonNode cargas = JSON.readTree(rest.getForEntity("/api/cargas", String.class).getBody());
        for (JsonNode carga : cargas) {
            if (carga.get("versao").asText().equals(versao)) {
                return carga;
            }
        }
        throw new IllegalStateException("a carga " + versao + " não está no GET /api/cargas");
    }

    // Envia os arquivos; com a carga vista, leva a versão e os dois instantes exatamente como o GET os devolveu.
    private ResponseEntity<String> importar(String versao, Map<String, String> arquivos, JsonNode vista) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("versao", versao);
        if (vista != null) {
            corpo.add("cargaDeOrigemEsperada", vista.get("versao").asText());
            corpo.add("origemImportadaEmEsperada", vista.get("importadoEm").asText());
            corpo.add("origemAlteradaEmEsperada", vista.get("alteradaEm").isNull() ? "" : vista.get("alteradaEm").asText());
        }
        arquivos.forEach((nome, conteudo) -> corpo.add("arquivos", arquivo(nome, conteudo)));
        return rest.postForEntity("/api/cargas", new HttpEntity<>(corpo, multipart()), String.class);
    }

    private static HttpHeaders multipart() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return cabecalhos;
    }

    private static ByteArrayResource arquivo(String nome, String conteudo) {
        return new ByteArrayResource(conteudo.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
    }

    // O conteúdo de uma tabela da carga, sem os identificadores, para comparar duas cargas.
    private String conteudo(String tabela, String versao) {
        return jdbc.queryForObject(("""
                select coalesce(string_agg((to_jsonb(t) - 'id' - 'carga_id')::text, '|'
                       order by (to_jsonb(t) - 'id' - 'carga_id')::text), '')
                  from %s t join carga_catalogo c on c.id = t.carga_id where c.versao = ?""").formatted(tabela),
                String.class, versao);
    }

    private List<String> naturezas(String versao) {
        return jdbc.queryForList("""
                select n.tabela || '=' || n.natureza from natureza_da_carga n
                  join carga_catalogo c on c.id = n.carga_id where c.versao = ? order by 1""", String.class, versao);
    }

    // Tudo o que a carga tem, inclusive a linha dela, para conferir que nada mudou.
    private String impressaoDigital(String versao) {
        StringBuilder impressao = new StringBuilder(jdbc.queryForObject(
                "select (to_jsonb(c) - 'id')::text from carga_catalogo c where c.versao = ?", String.class, versao));
        for (String tabela : List.of("classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
                "cobertura_catalogo", "natureza_da_carga", "anexo_declarado")) {
            impressao.append('#').append(conteudo(tabela, versao));
        }
        return impressao.toString();
    }

    private boolean existe(String versao) {
        return jdbc.queryForObject("select count(*) from carga_catalogo where versao = ?", Integer.class, versao) > 0;
    }

    private int contarCargas() {
        return jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class);
    }

    private static List<String> lista(JsonNode nos) {
        List<String> textos = new ArrayList<>();
        nos.forEach(no -> textos.add(no.asText()));
        return textos;
    }

    private static Map<String, String> catalogo(String dispositivo) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", classificacao(dispositivo, "NENHUM"));
        arquivos.put("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("aliquota-vigente.csv", "tributo;percentual;abrangencia;" + DADOS + "\n");
        return arquivos;
    }

    private static String classificacao(String dispositivo, String anexosAdmitidos) {
        return """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;%s;false;;;%s;FICTICIO;%s
                """.formatted(DADOS, dispositivo, VIGENCIA, anexosAdmitidos);
    }

    private static String aliquotas(String natureza) {
        return "tributo;percentual;abrangencia;" + DADOS + "\nCBS;99,99;ABRANGENCIA-XX;" + VIGENCIA + ";"
                + natureza + "\n";
    }
}
