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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// D019 (04/10/2026): o caso comum de a mesma nota vir duas vezes no lote, como -nfe.xml e -procNFe.xml. Antes da correção, a CLI gravava o recibo com 2 documentos enquanto o banco ficava com 1, a exportação caía com rastro de pilha, o item do primeiro arquivo era sobrescrito em silêncio quando o conteúdo divergia, e a web recusava o lote com uma mensagem de concorrência que não era a causa. Dados fictícios: a nota é o fixture nfe-item-completo.xml, cuja chave é feita só de 1; o par divergente é nfe-item-completo-reducao-60.xml, que tem a mesma chave e outra redução; o -nfe.xml é o mesmo documento sem o envelope, montado aqui.
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
class DocumentoRepetidoNoLoteTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String NOTA = "/documentos/nfe-item-completo.xml";
    private static final String MESMA_CHAVE_OUTRO_CONTEUDO = "/documentos/nfe-item-completo-reducao-60.xml";
    private static final String OUTRA_NOTA = "/documentos/nfe-multiplos-itens.xml";
    private static final String CHAVE_FICTICIA_REPETIDA = "1".repeat(44);

    private static final String NOME_NFE = "nota-nfe.xml";
    private static final String NOME_PROC = "nota-procNFe.xml";
    private static final String NOME_DIVERGENTE = "nota-divergente-procNFe.xml";
    private static final String NOME_OUTRA = "outra-nota.xml";

    private static final String ROTULO_DOS_REPETIDOS = "Documentos repetidos descartados";
    private static final String TIPO_DO_CONFLITO = "ChaveDeAcessoComConteudoDivergente";

    private static final String SENHA = "senha-ficticia-de-teste-0000";
    private static final String LOGIN = "teste.documento.repetido";

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
    private LinhaDeComando linhaDeComando;

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
        importacaoDeCatalogo.importar(catalogoFicticio());
        entrar();
        saida.limpar();
    }

    // Duplicata idêntica: a mesma nota com e sem o envelope de autorização conta uma vez, e a cópia descartada é reportada nas três saídas.
    @Test
    void duplicataIdenticaDeveSerContadaUmaVezEReportada() throws IOException {
        Path lote = Files.createDirectories(pasta.resolve("identica"));
        Files.writeString(lote.resolve(NOME_NFE), semEnvelope(texto(NOTA)), StandardCharsets.UTF_8);
        Files.writeString(lote.resolve(NOME_PROC), texto(NOTA), StandardCharsets.UTF_8);

        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        UUID execucao = unicaExecucao();

        assertThat(saida.texto()).contains("repetidos descartados . 1");

        JsonNode leitura = resultado(execucao).get("leitura");
        assertThat(leitura.get("documentosLidos").asInt()).isEqualTo(1);
        assertThat(leitura.get("documentosRepetidosDescartados").asInt()).isEqualTo(1);
        assertThat(leitura.get("arquivosIlegiveis").asInt()).isZero();
        assertThat(leitura.get("comoFoiALeitura").asText()).contains("1 arquivo(s) repetiam");

        assertThat(jdbc.queryForObject(
                "select documentos_duplicados from leitura_da_execucao where execucao_id = ?",
                Integer.class, execucao)).isEqualTo(1);
        assertThat(contar("select count(*) from documento")).isEqualTo(1);
    }

    // A exportação da execução resultante: sai sem exceção, e o total de apontamentos da planilha é o que está gravado.
    @Test
    void exportacaoDaExecucaoComDuplicataDeveSairInteira() throws IOException {
        Path lote = Files.createDirectories(pasta.resolve("exportar"));
        Files.writeString(lote.resolve(NOME_NFE), semEnvelope(texto(NOTA)), StandardCharsets.UTF_8);
        Files.writeString(lote.resolve(NOME_PROC), texto(NOTA), StandardCharsets.UTF_8);
        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        UUID execucao = unicaExecucao();

        Path destino = pasta.resolve("papel.xlsx");
        assertThatCode(() -> exportacao.exportar(execucao, destino)).doesNotThrowAnyException();

        int gravados = contar("select count(*) from achado_da_execucao where execucao_id = '" + execucao + "'");
        assertThat(gravados).as("o cenário precisa ter apontamento para a contagem significar algo").isPositive();
        try (Workbook planilha = new XSSFWorkbook(Files.newInputStream(destino))) {
            Sheet resumo = planilha.getSheet("Resumo");
            assertThat(valorAoLadoDe(resumo, "Documentos auditados")).isEqualTo("1");
            assertThat(valorAoLadoDe(resumo, ROTULO_DOS_REPETIDOS)).isEqualTo("1");
            assertThat(valorAoLadoDe(resumo, "Total de apontamentos")).isEqualTo(String.valueOf(gravados));
        }
    }

    // Duplicata divergente: mesma chave, conteúdo diferente. Nenhuma das duas é auditada nem gravada, e as duas aparecem como arquivos que ficaram de fora, com o motivo.
    @Test
    void duplicataDivergenteDeveSerConflitoReportadoSemEscolherNenhuma() throws IOException {
        Path lote = Files.createDirectories(pasta.resolve("divergente"));
        Files.writeString(lote.resolve(NOME_PROC), texto(NOTA), StandardCharsets.UTF_8);
        Files.writeString(lote.resolve(NOME_DIVERGENTE), texto(MESMA_CHAVE_OUTRO_CONTEUDO), StandardCharsets.UTF_8);
        Files.writeString(lote.resolve(NOME_OUTRA), texto(OUTRA_NOTA), StandardCharsets.UTF_8);

        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        UUID execucao = unicaExecucao();

        assertThat(contar("select count(*) from documento where chave_acesso = '" + CHAVE_FICTICIA_REPETIDA + "'"))
                .as("nenhuma das duas versões pode ser gravada: gravar uma seria escolher")
                .isZero();
        assertThat(contar("select count(*) from documento")).isEqualTo(1);

        List<String> tipos = jdbc.queryForList(
                "select tipo_de_erro from falha_de_leitura_da_execucao where execucao_id = ?", String.class, execucao);
        assertThat(tipos).containsExactly(TIPO_DO_CONFLITO, TIPO_DO_CONFLITO);

        JsonNode leitura = resultado(execucao).get("leitura");
        assertThat(leitura.get("documentosLidos").asInt()).isEqualTo(1);
        assertThat(leitura.get("arquivosIlegiveis").asInt()).isEqualTo(2);
        List<String> origens = new ArrayList<>();
        leitura.get("arquivosQueNaoForamLidos").forEach(arquivo -> origens.add(arquivo.get("origem").asText()));
        assertThat(origens).containsExactlyInAnyOrder(NOME_PROC, NOME_DIVERGENTE);
        assertThat(saida.texto()).contains(TIPO_DO_CONFLITO);

        Path destino = pasta.resolve("papel-divergente.xlsx");
        assertThatCode(() -> exportacao.exportar(execucao, destino)).doesNotThrowAnyException();
        try (Workbook planilha = new XSSFWorkbook(Files.newInputStream(destino))) {
            assertThat(textoDaAba(planilha.getSheet("Não lidos")))
                    .contains(NOME_PROC).contains(NOME_DIVERGENTE).contains("mesma chave de acesso");
        }
    }

    // A web recebe o mesmo par num .zip e não pode recusar o lote.
    @Test
    void aWebDeveAceitarOLoteComDuplicataIdentica() throws IOException {
        byte[] pacote = zipCom(List.of(
                new Entrada(NOME_NFE, semEnvelope(texto(NOTA)).getBytes(StandardCharsets.UTF_8)),
                new Entrada(NOME_PROC, texto(NOTA).getBytes(StandardCharsets.UTF_8))));

        ResponseEntity<String> resposta = enviar("lote.zip", pacote);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).as(resposta.getBody()).isTrue();
        JsonNode leitura = new ObjectMapper().readTree(resposta.getBody()).get("leitura");
        assertThat(leitura.get("documentosLidos").asInt()).isEqualTo(1);
        assertThat(leitura.get("documentosRepetidosDescartados").asInt()).isEqualTo(1);
    }

    // Execução já gravada com o recibo contradizendo o banco — o estado que a duplicata deixava antes da correção: a exportação recusa com mensagem, sem rastro de pilha, e sai com código de erro.
    @Test
    void exportacaoDeExecucaoInconsistenteDeveRecusarComMensagemESemPilha() throws IOException {
        Path lote = Files.createDirectories(pasta.resolve("inconsistente"));
        Files.writeString(lote.resolve(NOME_PROC), texto(NOTA), StandardCharsets.UTF_8);
        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        UUID execucao = unicaExecucao();
        jdbc.update("delete from achado_da_execucao where execucao_id = ?", execucao);
        saida.limpar();

        assertThatCode(() -> linhaDeComando.run(
                "exportar", "--arquivo=" + pasta.resolve("nao-sai.xlsx"), "--execucao=" + execucao))
                .doesNotThrowAnyException();

        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
        assertThat(saida.texto()).contains("O recibo da execução conta").contains("repetido");
    }

    private static String semEnvelope(String procNFe) {
        int inicio = procNFe.indexOf("<NFe>");
        int fim = procNFe.indexOf("</NFe>") + "</NFe>".length();
        assertThat(inicio).as("o fixture precisa ter o <NFe> dentro do envelope").isPositive();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + procNFe.substring(inicio, fim).replaceFirst(
                        "<NFe>", "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">");
    }

    private String texto(String recurso) throws IOException {
        try (InputStream entrada = getClass().getResourceAsStream(recurso)) {
            assertThat(entrada).as("recurso %s", recurso).isNotNull();
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private UUID unicaExecucao() {
        List<UUID> ids = jdbc.queryForList("select id from execucao_auditoria", UUID.class);
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    private JsonNode resultado(UUID execucao) throws IOException {
        ResponseEntity<String> resposta = rest.getForEntity("/api/analises/" + execucao, String.class);
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
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

    private record Entrada(String nome, byte[] conteudo) {
    }

    private static byte[] zipCom(List<Entrada> entradas) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Entrada entrada : entradas) {
                zip.putNextEntry(new ZipEntry(entrada.nome()));
                zip.write(entrada.conteudo());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
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

    private static String textoDaAba(Sheet aba) {
        assertThat(aba).as("aba da planilha").isNotNull();
        StringBuilder texto = new StringBuilder();
        for (Row linha : aba) {
            for (Cell celula : linha) {
                if (celula.getCellType() == CellType.STRING) {
                    texto.append(celula.getStringCellValue()).append('\n');
                }
            }
        }
        return texto.toString();
    }

    // Abre a sessão pelo caminho do navegador, como a SessaoDeTeste do pacote da API, que não é visível daqui.
    private void entrar() {
        if (usuarios.listar().stream().noneMatch(usuario -> usuario.login().equals(LOGIN))) {
            usuarios.criar(LOGIN, "Usuário de teste do documento repetido", Perfil.ADMINISTRADOR,
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
                "carga-ficticia-d019",
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
