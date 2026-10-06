package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ServicoDeExportacao;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// D023 (04/10/2026): a tolerância de valor da R05 é gravada junto da execução e aparece onde o resultado aparece — CLI, banco, API da conferência, da visão técnica e do histórico, e planilha. Antes, nada registrava qual valor tinha sido usado, e duas execuções com tolerâncias diferentes davam resultados diferentes sem que se pudesse ver por quê. A tolerância desta suíte, 0.07, é fictícia e diferente do padrão, de propósito. A execução "antiga" é simulada apagando as duas colunas.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "spring.jackson.default-property-inclusion=always",
                "auditoria.tolerancia-de-valor=0.07",
                "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
        })
@Testcontainers
@EnabledIf("dockerDisponivel")
class ToleranciaNosPontosDeSaidaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String NOTA = "/documentos/nfe-item-completo.xml";
    private static final String ROTULO_NA_PLANILHA = "Tolerância de valor (R05)";

    private static final String SENHA = "senha-ficticia-de-teste-0000";
    private static final String LOGIN = "teste.tolerancia";

    private static final String TABELAS = String.join(", ",
            "achado_evidencia", "achado", "tratativa",
            "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
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
    private ComandoAuditar comando;

    @Autowired
    private SaidaEmLista saida;

    @Autowired
    private ServicoDeExportacao exportacao;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacaoDeCatalogo;

    @Autowired
    private ServicoDeUsuarios usuarios;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path pasta;

    private UUID execucao;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void auditarPelaCli() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(catalogoFicticio());
        entrar();
        saida.limpar();
        Path lote = Files.createDirectories(pasta.resolve("lote"));
        try (InputStream conteudo = getClass().getResourceAsStream(NOTA)) {
            Files.write(lote.resolve("nota.xml"), conteudo.readAllBytes());
        }
        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        execucao = jdbc.queryForObject("select id from execucao_auditoria", UUID.class);
    }

    @Test
    void aCliDeveDizerATolerancia() {
        assertThat(saida.texto()).contains("tolerância R05").contains("0.07").contains("configurada");
    }

    @Test
    void oBancoDeveGuardarATolerancia() {
        assertThat(jdbc.queryForObject("select tolerancia_de_valor from execucao_auditoria where id = ?",
                java.math.BigDecimal.class, execucao)).isEqualByComparingTo("0.07");
        assertThat(jdbc.queryForObject("select origem_da_tolerancia from execucao_auditoria where id = ?",
                String.class, execucao)).isEqualTo("CONFIGURADA");
    }

    // Conferência, visão técnica (execução e lista) e histórico.
    @Test
    void aApiDeveDizerAToleranciaEmTodoResultado() throws IOException {
        assertTolerancia(get("/api/analises/" + execucao).get("leitura").get("toleranciaDeValor"));
        assertTolerancia(get("/api/execucoes/" + execucao).get("toleranciaDeValor"));
        assertTolerancia(get("/api/execucoes").get("execucoes").get(0).get("toleranciaDeValor"));
        assertTolerancia(get("/api/analises").get("linhas").get(0).get("toleranciaDeValor"));
    }

    // Onde o resultado da R05 aparece linha a linha: o detalhe do produto, com o passo de cada regra, e as listas de apontamentos e de não avaliados da visão técnica, que abastecem também o detalhe do achado. A execução da CLI não grava os itens (D020), então o produto vem de uma análise enviada pela interface.
    @Test
    void oDetalheDoProdutoEAsListasDaVisaoTecnicaDevemDizerATolerancia() throws IOException {
        byte[] nota;
        try (InputStream conteudo = getClass().getResourceAsStream(NOTA)) {
            nota = conteudo.readAllBytes();
        }
        ResponseEntity<String> criada = enviar("nota.xml", nota);
        assertThat(criada.getStatusCode().is2xxSuccessful()).as(criada.getBody()).isTrue();
        String analise = new ObjectMapper().readTree(criada.getBody()).get("id").asText();
        String endereco = get("/api/analises/" + analise + "/produtos").get("produtos").get(0).get("endereco").asText();

        assertTolerancia(get("/api/analises/" + analise + "/produtos/" + endereco).get("toleranciaDeValor"));
        assertTolerancia(get("/api/execucoes/" + execucao + "/achados").get("toleranciaDeValor"));
        assertTolerancia(get("/api/execucoes/" + execucao + "/nao-avaliados").get("toleranciaDeValor"));
    }

    @Test
    void aPlanilhaDeveDizerATolerancia() throws IOException {
        try (Workbook planilha = planilha()) {
            assertThat(valorAoLadoDe(planilha.getSheet("Resumo"), ROTULO_NA_PLANILHA))
                    .startsWith("0.07").contains("configurada");
        }
    }

    // Execução gravada antes desta correção: a tolerância não foi registrada, e nenhuma saída inventa um valor.
    @Test
    void execucaoSemToleranciaRegistradaDeveDizerQueNaoFoiRegistrada() throws IOException {
        jdbc.update("update execucao_auditoria set tolerancia_de_valor = null, origem_da_tolerancia = null");

        JsonNode tolerancia = get("/api/execucoes/" + execucao).get("toleranciaDeValor");
        assertThat(tolerancia.get("quantia").isNull()).isTrue();
        assertThat(tolerancia.get("motivoDaAusencia").asText()).contains("não registrada");
        assertThat(get("/api/analises/" + execucao).get("leitura").get("toleranciaDeValor").get("quantia").isNull())
                .isTrue();
        try (Workbook planilha = planilha()) {
            assertThat(valorAoLadoDe(planilha.getSheet("Resumo"), ROTULO_NA_PLANILHA)).startsWith("(não registrada");
        }
    }

    private static void assertTolerancia(JsonNode tolerancia) {
        assertThat(tolerancia).as("toleranciaDeValor na resposta").isNotNull();
        assertThat(tolerancia.get("quantia").asText()).isEqualTo("0.07");
        assertThat(tolerancia.get("origem").asText()).isEqualTo("CONFIGURADA");
        assertThat(tolerancia.get("texto").asText()).contains("0.07").contains("configurada");
    }

    private JsonNode get(String caminho) throws IOException {
        ResponseEntity<String> resposta = rest.getForEntity(caminho, String.class);
        assertThat(resposta.getStatusCode()).as(caminho + " " + resposta.getBody()).isEqualTo(HttpStatus.OK);
        return new ObjectMapper().readTree(resposta.getBody());
    }

    private ResponseEntity<String> enviar(String nome, byte[] conteudo) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        });
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/analises", new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private Workbook planilha() throws IOException {
        Path destino = pasta.resolve("papel-" + System.nanoTime() + ".xlsx");
        exportacao.exportar(execucao, destino);
        return new XSSFWorkbook(Files.newInputStream(destino));
    }

    // Método auxiliar que acha a linha pelo rótulo na coluna A e devolve a coluna B como texto.
    private static String valorAoLadoDe(Sheet aba, String rotulo) {
        for (Row linha : aba) {
            Cell primeira = linha.getCell(0);
            if (primeira != null && primeira.getCellType() == CellType.STRING
                    && primeira.getStringCellValue().equals(rotulo)) {
                Cell valor = linha.getCell(1);
                assertThat(valor).as("valor ao lado de \"%s\"", rotulo).isNotNull();
                return valor.getCellType() == CellType.NUMERIC
                        ? String.valueOf((long) valor.getNumericCellValue())
                        : valor.getStringCellValue();
            }
        }
        throw new AssertionError("a aba %s não tem a linha \"%s\"".formatted(aba.getSheetName(), rotulo));
    }

    // Abre a sessão pelo caminho do navegador, como a SessaoDeTeste do pacote da API, que não é visível daqui.
    private void entrar() {
        if (usuarios.listar().stream().noneMatch(usuario -> usuario.login().equals(LOGIN))) {
            usuarios.criar(LOGIN, "Usuário de teste da tolerância", Perfil.ADMINISTRADOR,
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

    private static CargaDeCatalogo catalogoFicticio() {
        ProcedenciaNormativa procedencia =
                ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA PARA TESTE v0.0");
        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("999999"),
                Set.of(new CodigoCst("AAA")),
                "Dispositivo ficticio para teste",
                false,
                Optional.empty(),
                List.of(),
                procedencia);
        RegistroNcm registroNcm = new RegistroNcm(new Ncm("00000000"), "Descricao ficticia de teste", procedencia);
        return new CargaDeCatalogo(
                "carga-ficticia-d023",
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(
                        Natureza.FICTICIO, List.of(classificacao), List.of(registroNcm), List.of(), List.of()),
                List.of(classificacao),
                List.of(registroNcm),
                List.of(),
                List.of());
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
