package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

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

/**
 * A tratativa pela web, gravando quem decidiu e quando, e a correção de uma
 * análise como análise nova com vínculo à anterior.
 *
 * <p>Dados fictícios: cClassTrib {@code 999999}, CST admitido {@code AAA} — o
 * documento de teste declara {@code 999}, o que produz apontamento para tratar —,
 * NCM {@code 00000000}, vigência a partir de 1900.</p>
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
class TratativaECorrecaoPelaApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO = "/documentos/nfe-item-completo.xml";
    private static final String VIGENCIA = "1900-01-01;;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

    private static final String TABELAS = String.join(", ",
            "correcao_de_analise", "achado_evidencia", "achado_da_execucao", "achado", "tratativa",
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
    private JdbcTemplate jdbc;

    private String analise;
    private String achado;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void analisarUmaNotaComApontamento() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        ResponseEntity<String> carga = importarCatalogo();
        assertThat(carga.getStatusCode().value()).as(carga.getBody()).isEqualTo(201);
        analise = campo(enviar("/api/analises").getBody(), "id");
        String achados = rest.getForEntity("/api/execucoes/" + analise + "/achados", String.class).getBody();
        achado = campo(achados, "id");
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void aTratativaDeveGravarQuemDecidiuEQuando() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);

        ResponseEntity<String> resposta = tratar("ACEITO", "Justificativa ficticia do fiscal.");

        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        assertThat(resposta.getBody())
                .contains("\"decisao\":\"ACEITO\"")
                .contains("\"login\":\"teste.fiscal\"")
                .contains("\"registradoEm\":\"")
                .contains("\"motivoDoAutorAusente\":null");
    }

    @Test
    void quemDecidiuVemDaSessaoENuncaDoCorpoDoPedido() {
        Usuario administrador = SessaoDeTeste.garantirUsuario(usuarios, Perfil.ADMINISTRADOR);
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);

        ResponseEntity<String> resposta = pedir(HttpMethod.POST, "/api/achados/" + achado + "/tratativas",
                "{\"decisao\":\"ACEITO\",\"justificativa\":\"x\",\"autorId\":\"%s\",\"autor\":\"%s\"}"
                        .formatted(administrador.id(), administrador.login()));

        assertThat(resposta.getBody()).contains("\"login\":\"teste.fiscal\"")
                .doesNotContain("\"login\":\"teste.administrador\"");
    }

    @Test
    void consultaNaoDeveRegistrarTratativaENadaDeveSerGravado() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.CONSULTA);

        ResponseEntity<String> resposta = tratar("ACEITO", "Tentativa de quem só lê.");

        assertThat(resposta.getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from tratativa_registro", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tratativa", Integer.class)).isZero();
    }

    @Test
    void tratarDeNovoDeveAcrescentarAoHistoricoSemApagarQuemDecidiuAntes() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);
        tratar("ACEITO", "Primeira decisao ficticia.");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        tratar("REFUTADO", "Segunda decisao ficticia.");

        String historico = rest.getForEntity("/api/achados/" + achado + "/tratativas", String.class).getBody();

        assertThat(historico.indexOf("teste.fiscal"))
                .as("as duas decisões estão lá, na ordem em que foram dadas")
                .isNotNegative()
                .isLessThan(historico.indexOf("teste.administrador"));
        assertThat(historico).contains("\"decisao\":\"ACEITO\"").contains("\"decisao\":\"REFUTADO\"");
        String achados = rest.getForEntity("/api/execucoes/" + analise + "/achados", String.class).getBody();
        assertThat(achados)
                .as("a decisão que vale é a última")
                .contains("\"statusDeTratativa\":\"REFUTADO\"");
    }

    @Test
    void aJustificativaContinuaOmitidaDaRespostaPorPadrao() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);
        tratar("ACEITO", "Justificativa com CNPJ 99.999.999/9999-99.");

        String historico = rest.getForEntity("/api/achados/" + achado + "/tratativas", String.class).getBody();

        assertThat(historico)
                .doesNotContain("99.999.999/9999-99")
                .contains("\"justificativa\":null")
                .contains("auditoria.api.expor-justificativa");
    }

    @Test
    void excluirQuemDecidiuDeveDesativarEOHistoricoContinuaComONomeDele() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);
        tratar("ACEITO", "Decisao ficticia.");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        Usuario fiscal = SessaoDeTeste.garantirUsuario(usuarios, Perfil.FISCAL);

        String exclusao = pedir(HttpMethod.DELETE, "/api/usuarios/" + fiscal.id(), null).getBody();
        String historico = rest.getForEntity("/api/achados/" + achado + "/tratativas", String.class).getBody();

        assertThat(exclusao).contains("DESATIVADO");
        assertThat(historico).contains("\"login\":\"teste.fiscal\"").contains("\"ativo\":false");
    }

    @Test
    void corrigirDeveGerarAnaliseNovaComVinculoEAAnteriorContinuaComOHashDela() {
        String hashAntes = jdbc.queryForObject(
                "select hash_entrada from execucao_auditoria where id = ?::uuid", String.class, analise);
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);

        ResponseEntity<String> correcao = enviar("/api/analises/" + analise + "/correcoes");

        assertThat(correcao.getStatusCode().value()).isEqualTo(201);
        String nova = campo(correcao.getBody(), "id");
        assertThat(nova).isNotEqualTo(analise);
        assertThat(rest.getForEntity("/api/analises/" + nova + "/vinculos", String.class).getBody())
                .contains("\"corrige\":\"" + analise + "\"");
        assertThat(rest.getForEntity("/api/analises/" + analise + "/vinculos", String.class).getBody())
                .contains("\"corrigidaPor\":[\"" + nova + "\"]")
                .contains("\"corrige\":null");
        assertThat(jdbc.queryForObject(
                "select hash_entrada from execucao_auditoria where id = ?::uuid", String.class, analise))
                .as("a análise anterior continua correspondendo ao que ela processou")
                .isEqualTo(hashAntes);
    }

    @Test
    void consultaNaoDeveCorrigirAnalise() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.CONSULTA);

        assertThat(enviar("/api/analises/" + analise + "/correcoes").getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from execucao_auditoria", Integer.class)).isEqualTo(1);
    }

    private ResponseEntity<String> tratar(String decisao, String justificativa) {
        return pedir(HttpMethod.POST, "/api/achados/" + achado + "/tratativas",
                "{\"decisao\":\"%s\",\"justificativa\":\"%s\"}".formatted(decisao, justificativa));
    }

    private ResponseEntity<String> pedir(HttpMethod metodo, String caminho, String corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(caminho, metodo,
                corpo == null ? new HttpEntity<>(cabecalhos) : new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private ResponseEntity<String> enviar(String caminho) {
        byte[] conteudo;
        try (InputStream documento = getClass().getResourceAsStream(DOCUMENTO)) {
            conteudo = documento.readAllBytes();
        } catch (IOException naoLeu) {
            throw new IllegalStateException(naoLeu);
        }
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add(ControladorDeAnalises.CAMPO_DO_ARQUIVO, arquivo("nota.xml", conteudo));
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity(caminho, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private ResponseEntity<String> importarCatalogo() {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s
                999999;AAA;Dispositivo ficticio;false;;;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("aliquota-vigente.csv", "tributo;percentual;abrangencia;" + DADOS + "\n");

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("versao", "carga-ficticia");
        arquivos.forEach((nome, conteudo) ->
                corpo.add("arquivos", arquivo(nome, conteudo.getBytes(StandardCharsets.UTF_8))));
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/cargas", new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static ByteArrayResource arquivo(String nome, byte[] conteudo) {
        return new ByteArrayResource(conteudo) {
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
