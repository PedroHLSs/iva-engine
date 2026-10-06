package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;

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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A imutabilidade da carga de catálogo depois de usada, pela API e contra
 * PostgreSQL de verdade.
 *
 * <p>O motivo não é purismo: cada análise é reaberta com a carga que ela
 * efetivamente usou. Alterar uma linha de carga usada mudaria o fundamento que uma
 * análise passada cita, sem aviso nenhum. O teste central aqui é
 * {@link #aAnaliseAntigaReabertaDeveContinuarCitandoACargaQueUsou}: a edição cria
 * carga nova, uma análise nova a usa, e a antiga continua citando a original.</p>
 *
 * <p>Dados fictícios: cClassTrib {@code 999999}, CST {@code 999}, NCM
 * {@code 00000000}, vigência a partir de 1900, os mesmos do documento de teste.</p>
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
class CargasPelaApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO = "/documentos/nfe-item-completo.xml";
    private static final String VIGENCIA = "1900-01-01;;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";
    private static final String DISPOSITIVO_ORIGINAL = "Dispositivo ficticio da carga original";
    private static final String DISPOSITIVO_DO_RASCUNHO = "Dispositivo ficticio alterado no rascunho";
    private static final String DISPOSITIVO_DA_EDICAO = "Dispositivo ficticio da versao editada";

    private static final String TABELAS = String.join(", ",
            "correcao_de_analise", "achado_evidencia", "achado_da_execucao", "achado", "tratativa",
            "avaliacao_nao_concluida", "item_da_execucao", "falha_de_leitura_da_execucao",
            "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "natureza_da_carga", "cobertura_catalogo", "carga_catalogo");

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
        jdbc.execute("truncate table " + TABELAS + " cascade");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void rascunhoDeveSerEditadoNoLugarEExcluidoLivremente() {
        assertThat(importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL)).getStatusCode().value()).isEqualTo(201);

        String estado = rest.getForEntity("/api/cargas/carga-a", String.class).getBody();
        assertThat(estado)
                .contains("\"selada\":false")
                .contains("\"efeito\":\"ALTERAR_RASCUNHO\"")
                .contains("\"permitida\":true");

        ResponseEntity<String> edicao = editar("carga-a", "ALTERAR_RASCUNHO", null,
                Map.of("classificacao-tributaria.csv", classificacao(DISPOSITIVO_DO_RASCUNHO),
                        // D026 (04/10/2026), decisão D-a: trocar classificacao-tributaria.csv exige cobertura.csv junto.
                        "cobertura.csv", catalogo(DISPOSITIVO_DO_RASCUNHO).get("cobertura.csv")));
        assertThat(edicao.getStatusCode().value()).isEqualTo(200);
        assertThat(edicao.getBody()).contains("\"versaoResultante\":\"carga-a\"");
        assertThat(dispositivoGravado("carga-a")).isEqualTo(DISPOSITIVO_DO_RASCUNHO);

        ResponseEntity<String> exclusao = rest.exchange("/api/cargas/carga-a", HttpMethod.DELETE, null, String.class);
        assertThat(exclusao.getStatusCode().value()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class)).isZero();
    }

    @Test
    void aAnaliseDeveSelarACargaEAExclusaoDeveDizerQuantasAnalisesDependemDela() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        analisar();
        analisar();

        ResponseEntity<String> exclusao = rest.exchange("/api/cargas/carga-a", HttpMethod.DELETE, null, String.class);

        assertThat(exclusao.getStatusCode().value()).isEqualTo(409);
        assertThat(exclusao.getBody()).contains("CARGA_SELADA").contains("é usada por 2 análise(s)");
        assertThat(jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class)).isEqualTo(1);
    }

    @Test
    void aTelaDeveSaberAntesDeConfirmarQueSalvarCriaraVersaoNova() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        analisar();

        String estado = rest.getForEntity("/api/cargas/carga-a", String.class).getBody();

        assertThat(estado)
                .contains("\"selada\":true")
                .contains("\"analisesQueUsam\":1")
                .contains("\"efeito\":\"CRIAR_VERSAO_NOVA\"")
                .contains("\"versaoQueSeraCriada\":\"carga-a-ed1\"")
                .contains("é usada por 1 análise(s). Salvar criará a versão \\\"carga-a-ed1\\\"")
                .contains("\"permitida\":false");
    }

    @Test
    void editarCargaUsadaDeveCriarVersaoNovaEPreservarAOriginalIntacta() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        analisar();
        String antes = impressaoDigitalDaCarga("carga-a");

        ResponseEntity<String> edicao = editar("carga-a", "CRIAR_VERSAO_NOVA", "carga-a-ed1",
                Map.of("classificacao-tributaria.csv", classificacao(DISPOSITIVO_DA_EDICAO),
                        // D026 (04/10/2026), decisão D-a: trocar classificacao-tributaria.csv exige cobertura.csv junto.
                        "cobertura.csv", catalogo(DISPOSITIVO_DA_EDICAO).get("cobertura.csv")));

        assertThat(edicao.getStatusCode().value()).isEqualTo(200);
        assertThat(edicao.getBody())
                .contains("\"efeito\":\"CRIAR_VERSAO_NOVA\"")
                .contains("\"versaoResultante\":\"carga-a-ed1\"");
        assertThat(impressaoDigitalDaCarga("carga-a"))
                .as("nenhuma linha da carga original mudou")
                .isEqualTo(antes);
        assertThat(dispositivoGravado("carga-a")).isEqualTo(DISPOSITIVO_ORIGINAL);
        assertThat(dispositivoGravado("carga-a-ed1")).isEqualTo(DISPOSITIVO_DA_EDICAO);
        assertThat(jdbc.queryForObject("select origem.versao from carga_catalogo c join carga_catalogo origem"
                + " on origem.id = c.derivada_de where c.versao = 'carga-a-ed1'", String.class))
                .isEqualTo("carga-a");
        assertThat(jdbc.queryForObject("select count(*) from registro_ncm n join carga_catalogo c"
                + " on c.id = n.carga_id where c.versao = 'carga-a-ed1'", Integer.class))
                .as("a tabela que não veio no envio foi copiada da origem")
                .isEqualTo(1);
    }

    @Test
    void aAnaliseAntigaReabertaDeveContinuarCitandoACargaQueUsou() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        String antiga = analisar();
        editar("carga-a", "CRIAR_VERSAO_NOVA", "carga-a-ed1",
                Map.of("classificacao-tributaria.csv", classificacao(DISPOSITIVO_DA_EDICAO),
                        // D026 (04/10/2026), decisão D-a: trocar classificacao-tributaria.csv exige cobertura.csv junto.
                        "cobertura.csv", catalogo(DISPOSITIVO_DA_EDICAO).get("cobertura.csv")));
        String nova = analisar();

        String detalheDaAntiga = detalheDoPrimeiroProduto(antiga);
        String detalheDaNova = detalheDoPrimeiroProduto(nova);

        assertThat(detalheDaAntiga)
                .as("a análise antiga cita a carga que ela usou, e o fundamento daquela carga")
                .contains("\"versaoDoCatalogo\":\"carga-a\"")
                .contains(DISPOSITIVO_ORIGINAL)
                .doesNotContain(DISPOSITIVO_DA_EDICAO)
                .doesNotContain("carga-a-ed1");
        assertThat(detalheDaNova)
                .as("a análise nova usou a versão editada, que passou a ser a mais recente")
                .contains("\"versaoDoCatalogo\":\"carga-a-ed1\"")
                .contains(DISPOSITIVO_DA_EDICAO);
    }

    @Test
    void edicaoComOEfeitoQueATelaMostrouAntesDoSeloDeveSerRecusadaSemGravarNada() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        analisar();

        ResponseEntity<String> edicao = editar("carga-a", "ALTERAR_RASCUNHO", null,
                Map.of("classificacao-tributaria.csv", classificacao(DISPOSITIVO_DA_EDICAO)));

        assertThat(edicao.getStatusCode().value()).isEqualTo(409);
        assertThat(edicao.getBody())
                .contains("EDICAO_DESATUALIZADA")
                .contains("Nada foi gravado")
                .contains("\"previaAtual\"")
                .contains("\"efeito\":\"CRIAR_VERSAO_NOVA\"");
        assertThat(dispositivoGravado("carga-a")).isEqualTo(DISPOSITIVO_ORIGINAL);
        assertThat(jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class)).isEqualTo(1);
    }

    @Test
    void oSeloDeveSobreviverAApagarAsAnalisesEAMensagemDizQueRelatoriosPodemCitala() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        analisar();
        jdbc.execute("truncate table execucao_auditoria cascade");

        String estado = rest.getForEntity("/api/cargas/carga-a", String.class).getBody();
        ResponseEntity<String> exclusao = rest.exchange("/api/cargas/carga-a", HttpMethod.DELETE, null, String.class);

        assertThat(estado).contains("\"selada\":true").contains("\"analisesQueUsam\":0");
        assertThat(exclusao.getStatusCode().value()).isEqualTo(409);
        assertThat(exclusao.getBody())
                .contains("foi entregue a uma análise")
                .contains("relatórios exportados podem citá-la");
    }

    @Test
    void oBancoDeveRecusarMexerNaCargaSeladaMesmoSemPassarPelaAplicacao() throws IOException {
        importar("carga-a", catalogo(DISPOSITIVO_ORIGINAL));
        analisar();

        assertThatThrownBy(() -> jdbc.update("update classificacao_tributaria set dispositivo_legal = 'outro'"))
                .hasMessageContaining("está selada");
        assertThatThrownBy(() -> jdbc.update("delete from registro_ncm"))
                .hasMessageContaining("está selada");
        assertThatThrownBy(() -> jdbc.update("delete from carga_catalogo"))
                .hasMessageContaining("não pode ser excluída");
        assertThatThrownBy(() -> jdbc.update("update carga_catalogo set selada_em = null"))
                .hasMessageContaining("não pode ser retirado");
        assertThat(dispositivoGravado("carga-a")).isEqualTo(DISPOSITIVO_ORIGINAL);
    }

    @Test
    void aImportacaoDeveRecusarACargaInteiraListandoLinhaColunaEValor() {
        Map<String, String> arquivos = new LinkedHashMap<>(catalogo(DISPOSITIVO_ORIGINAL));
        arquivos.put("registro-ncm.csv", """
                ncm;descricao;%s
                123;Descricao ficticia;%s
                00000000;Descricao ficticia;%s
                """.formatted(DADOS, VIGENCIA + ";FICTICIO", VIGENCIA + ";"));

        ResponseEntity<String> resposta = importar("carga-com-erro", arquivos);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody())
                .contains("\"erro\":\"CARGA_RECUSADA\"")
                .contains("\"arquivo\":\"registro-ncm.csv\",\"linha\":2,\"coluna\":\"ncm\",\"valor\":\"123\"")
                .contains("\"arquivo\":\"registro-ncm.csv\",\"linha\":3,\"coluna\":\"natureza\"");
        assertThat(jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class))
                .as("nada foi gravado")
                .isZero();
    }

    @Test
    void aImportacaoDeveRecusarCoberturaSobreTabelaVazia() {
        Map<String, String> arquivos = new LinkedHashMap<>(catalogo(DISPOSITIVO_ORIGINAL));
        arquivos.put("item-anexo.csv", "ncm;identificadorDoAnexo;tipoDeTratamento;" + DADOS + "\n");

        ResponseEntity<String> resposta = importar("carga-sem-anexo", arquivos);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("ITEM_ANEXO").contains("tabela sem nenhum registro");
    }

    private ResponseEntity<String> importar(String versao, Map<String, String> arquivos) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("versao", versao);
        arquivos.forEach((nome, conteudo) -> corpo.add("arquivos", arquivo(nome, conteudo)));
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/cargas", new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private ResponseEntity<String> editar(
            String versao, String efeito, String versaoNova, Map<String, String> arquivos) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("efeitoEsperado", efeito);
        if (versaoNova != null) {
            corpo.add("versaoNova", versaoNova);
        }
        arquivos.forEach((nome, conteudo) -> corpo.add("arquivos", arquivo(nome, conteudo)));
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/cargas/" + versao, HttpMethod.PUT, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private String analisar() throws IOException {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        byte[] conteudo;
        try (InputStream documento = getClass().getResourceAsStream(DOCUMENTO)) {
            conteudo = documento.readAllBytes();
        }
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
        return campo(resposta.getBody(), "id");
    }

    private String detalheDoPrimeiroProduto(String analise) {
        String produtos = rest.getForEntity("/api/analises/" + analise + "/produtos", String.class).getBody();
        return rest.getForEntity(
                "/api/analises/" + analise + "/produtos/" + campo(produtos, "endereco"), String.class).getBody();
    }

    private String dispositivoGravado(String versao) {
        return jdbc.queryForObject("select t.dispositivo_legal from classificacao_tributaria t join carga_catalogo c"
                + " on c.id = t.carga_id where c.versao = ?", String.class, versao);
    }

    /** Resumo de todas as linhas da carga em todas as tabelas, para provar que nenhuma mudou. */
    private String impressaoDigitalDaCarga(String versao) {
        return jdbc.queryForObject("""
                select md5(
                  coalesce((select string_agg(t::text, '|' order by t.id) from classificacao_tributaria t
                            where t.carga_id = c.id), '') ||
                  coalesce((select string_agg(t::text, '|' order by t.id) from registro_ncm t
                            where t.carga_id = c.id), '') ||
                  coalesce((select string_agg(t::text, '|' order by t.id) from item_anexo t
                            where t.carga_id = c.id), '') ||
                  coalesce((select string_agg(t::text, '|' order by t.id) from aliquota_vigente t
                            where t.carga_id = c.id), '') ||
                  coalesce((select string_agg(t::text, '|' order by t.tabela) from cobertura_catalogo t
                            where t.carga_id = c.id), '') ||
                  coalesce((select string_agg(t::text, '|' order by t.tabela) from natureza_da_carga t
                            where t.carga_id = c.id), '') ||
                  c.versao || c.importado_em::text)
                from carga_catalogo c where c.versao = ?""", String.class, versao);
    }

    private static Map<String, String> catalogo(String dispositivo) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", classificacao(dispositivo));
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

    private static String classificacao(String dispositivo) {
        return """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s
                999999;999;%s;false;;;%s;FICTICIO
                """.formatted(DADOS, dispositivo, VIGENCIA);
    }

    private static ByteArrayResource arquivo(String nome, String conteudo) {
        return new ByteArrayResource(conteudo.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
    }

    private static String campo(String json, String nome) {
        Matcher achado = Pattern.compile("\"" + nome + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(achado.find()).as("campo %s em %s", nome, json).isTrue();
        return achado.group(1);
    }
}
