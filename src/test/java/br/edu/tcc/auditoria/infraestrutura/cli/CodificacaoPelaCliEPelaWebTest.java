package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// D024 (04/10/2026): o mesmo catálogo, com o registro-ncm.csv em Windows-1252, entra pelo comando importar-catalogo e pelo POST /api/cargas. Os dois recusam, com a mesma mensagem dizendo que a codificação esperada é UTF-8, e nada é gravado. Antes, a CLI caía com MalformedInputException embrulhada e a web gravava a descrição com "?" no lugar dos acentos. Todos os valores são fictícios.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "spring.jackson.default-property-inclusion=always",
                "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
        })
@Testcontainers
@EnabledIf("dockerDisponivel")
class CodificacaoPelaCliEPelaWebTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String VIGENCIA = "1900-01-01;1900-12-31";
    private static final String NCM = "registro-ncm.csv";
    private static final String SENHA = "senha-ficticia-de-teste-0000";
    private static final String LOGIN = "teste.codificacao";

    private static final String TABELAS = String.join(", ",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "cobertura_catalogo", "natureza_da_carga", "carga_catalogo");

    @TestConfiguration
    static class SaidaCapturada {

        // Substitui a saída do terminal, para o texto da CLI poder ser conferido.
        @Bean
        @Primary
        SaidaEmLista saidaEmLista() {
            return new SaidaEmLista();
        }
    }

    @Autowired
    private LinhaDeComando linhaDeComando;

    @Autowired
    private SaidaEmLista saida;

    @Autowired
    private ServicoDeUsuarios usuarios;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path pasta;

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
        entrar();
        saida.limpar();
    }

    @Test
    void oMesmoArquivoEmWindows1252DeveTerAMesmaRecusaNaCliENaWeb() throws IOException {
        Map<String, byte[]> arquivos = catalogo(Charset.forName("windows-1252"));

        Path diretorio = Files.createDirectories(pasta.resolve("catalogo"));
        for (Map.Entry<String, byte[]> arquivo : arquivos.entrySet()) {
            Files.write(diretorio.resolve(arquivo.getKey()), arquivo.getValue());
        }
        assertThatCode(() -> linhaDeComando.run(
                "importar-catalogo", "--diretorio=" + diretorio, "--versao=carga-pela-cli"))
                .as("a CLI recusa com mensagem, sem rastro de pilha")
                .doesNotThrowAnyException();
        String pelaCli = saida.texto();

        ResponseEntity<String> pelaWeb = importarPelaWeb("carga-pela-web", arquivos);
        JsonNode corpo = new ObjectMapper().readTree(pelaWeb.getBody());

        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
        assertThat(pelaWeb.getStatusCode()).as(pelaWeb.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(corpo.get("erro").asText()).isEqualTo("CARGA_RECUSADA");
        assertThat(corpo.get("mensagem").asText()).contains(NCM).contains("UTF-8");
        assertThat(pelaCli.strip()).isEqualTo(corpo.get("mensagem").asText().strip());
        assertThat(jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from registro_ncm", Integer.class)).isZero();
    }

    // Controle: o mesmo catálogo em UTF-8 entra pelas duas portas, e o acento é gravado como veio.
    @Test
    void emUtf8DeveEntrarPelasDuasPortasComOAcento() throws IOException {
        Map<String, byte[]> arquivos = catalogo(StandardCharsets.UTF_8);
        Path diretorio = Files.createDirectories(pasta.resolve("catalogo-utf8"));
        for (Map.Entry<String, byte[]> arquivo : arquivos.entrySet()) {
            Files.write(diretorio.resolve(arquivo.getKey()), arquivo.getValue());
        }

        linhaDeComando.run("importar-catalogo", "--diretorio=" + diretorio, "--versao=carga-pela-cli");
        ResponseEntity<String> pelaWeb = importarPelaWeb("carga-pela-web", arquivos);

        assertThat(linhaDeComando.getExitCode()).as(saida.texto()).isZero();
        assertThat(pelaWeb.getStatusCode().is2xxSuccessful()).as(pelaWeb.getBody()).isTrue();
        assertThat(jdbc.queryForList("select descricao from registro_ncm", String.class))
                .containsExactly("DESCRIÇÃO FICTÍCIA", "DESCRIÇÃO FICTÍCIA");
    }

    private ResponseEntity<String> importarPelaWeb(String versao, Map<String, byte[]> arquivos) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("versao", versao);
        arquivos.forEach((nome, conteudo) -> corpo.add("arquivos", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        }));
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/cargas", new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    // Os cinco arquivos em UTF-8, menos o de NCM, escrito na codificação pedida.
    private static Map<String, byte[]> catalogo(Charset codificacaoDoNcm) {
        Map<String, byte[]> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", utf8("""
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FONTE FICTICIA v0.0;FICTICIO
                NCM;%1$s;FONTE FICTICIA v0.0;FICTICIO
                ITEM_ANEXO;%1$s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        arquivos.put("classificacao-tributaria.csv", utf8("""
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX000;AAA;Dispositivo ficticio;false;;NENHUM;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        arquivos.put(NCM, """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;DESCRIÇÃO FICTÍCIA;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA).getBytes(codificacaoDoNcm));
        arquivos.put("item-anexo.csv", utf8("""
                ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        arquivos.put("aliquota-vigente.csv", utf8("""
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CBS;99,99;ABRANGENCIA-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        return arquivos;
    }

    private static byte[] utf8(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    // Abre a sessão pelo caminho do navegador, como a SessaoDeTeste do pacote da API, que não é visível daqui.
    private void entrar() {
        if (usuarios.listar().stream().noneMatch(usuario -> usuario.login().equals(LOGIN))) {
            usuarios.criar(LOGIN, "Usuário de teste da codificação", Perfil.ADMINISTRADOR,
                    new SenhaInformada(SENHA));
        }
        rest.getRestTemplate().getInterceptors().clear();
        ResponseEntity<String> token = rest.getForEntity("/api/sessao/csrf", String.class);
        String cookieAnonimo = cookieDaSessao(token.getHeaders()).orElseThrow();
        JsonNode corpoDoToken;
        try {
            corpoDoToken = new ObjectMapper().readTree(token.getBody());
        } catch (IOException naoEraJson) {
            throw new IllegalStateException(naoEraJson);
        }
        String cabecalho = corpoDoToken.get("cabecalho").asText();
        String valor = corpoDoToken.get("token").asText();

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.add(HttpHeaders.COOKIE, cookieAnonimo);
        cabecalhos.add(cabecalho, valor);
        ResponseEntity<String> entrada = rest.exchange("/api/sessao", HttpMethod.POST,
                new HttpEntity<>("{\"login\":\"" + LOGIN + "\",\"senha\":\"" + SENHA + "\"}", cabecalhos),
                String.class);
        assertThat(entrada.getStatusCode().is2xxSuccessful()).as(entrada.getBody()).isTrue();
        String cookie = cookieDaSessao(entrada.getHeaders()).orElse(cookieAnonimo);

        rest.getRestTemplate().getInterceptors().add((pedido, corpo, execucao) -> {
            pedido.getHeaders().set(HttpHeaders.COOKIE, cookie);
            pedido.getHeaders().set(cabecalho, valor);
            return execucao.execute(pedido, corpo);
        });
    }

    private static Optional<String> cookieDaSessao(HttpHeaders cabecalhos) {
        return Optional.ofNullable(cabecalhos.get(HttpHeaders.SET_COOKIE)).orElse(List.of()).stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst();
    }

    // Saída que guarda as linhas em memória.
    static final class SaidaEmLista implements Saida {

        private final List<String> linhas = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        void limpar() {
            linhas.clear();
        }

        String texto() {
            synchronized (linhas) {
                return String.join("\n", linhas);
            }
        }
    }
}
