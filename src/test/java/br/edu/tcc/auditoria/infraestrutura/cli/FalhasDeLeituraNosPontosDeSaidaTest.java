package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.analise.AnaliseInvalida;
import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D018 (04/10/2026): o cenário demonstrado na revisão — três arquivos, dois corrompidos, auditados pela CLI — conferido nos quatro pontos de saída: o texto da CLI, a tabela falha_de_leitura_da_execucao, a API (que é o que a tela lê) e a planilha. Antes da correção a CLI dizia 2, o banco ficava com 0 linhas, a API dizia 0 e "Nenhum arquivo deixou de ser lido", e a planilha não mencionava os dois. Dados fictícios: a nota boa e um corrompido são os fixtures de sempre, e o segundo corrompido é escrito aqui, sem conteúdo fiscal nenhum.
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
class FalhasDeLeituraNosPontosDeSaidaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO_BOM = "/documentos/nfe-item-completo.xml";
    private static final String DOCUMENTO_CORROMPIDO = "/documentos/documento-corrompido.xml";
    private static final String NOME_DO_BOM = "nota-boa.xml";
    private static final String NOME_DO_PRIMEIRO_CORROMPIDO = "primeiro-corrompido.xml";
    private static final String NOME_DO_SEGUNDO_CORROMPIDO = "segundo-corrompido.xml";

    private static final String NADA_FALHOU = "Nenhum arquivo deixou de ser lido";
    private static final String ROTULO_DOS_NAO_LIDOS = "Arquivos que não puderam ser lidos";

    private static final String SENHA = "senha-ficticia-de-teste-0000";
    private static final String LOGIN = "teste.falhas.de.leitura";

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
    private ServicoDeAuditoria auditoriaSemRegistroDaLeitura;

    @Autowired
    private ServicoDeAnalise analiseDaInterface;

    @Autowired
    private ConsultaDoAcervoDaAnalise acervo;

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

    // O cenário demonstrado, nos quatro pontos de saída.
    @Test
    void osDoisArquivosIlegiveisDevemAparecerNaCliNoBancoNaApiENaPlanilha() throws IOException {
        Path lote = loteComUmBomEDoisCorrompidos();

        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        UUID execucao = unicaExecucao();

        // 1. CLI.
        assertThat(saida.texto())
                .contains("2 arquivo(s) não puderam ser lidos")
                .doesNotContain(NADA_FALHOU);

        // 2. Banco: as duas falhas gravadas junto da execução.
        List<String> origens = jdbc.queryForList(
                "select origem from falha_de_leitura_da_execucao where execucao_id = ? order by ordem",
                String.class, execucao);
        assertThat(origens).containsExactlyInAnyOrder(NOME_DO_PRIMEIRO_CORROMPIDO, NOME_DO_SEGUNDO_CORROMPIDO);

        // 3. API: o bloco de leitura que a tela desenha.
        JsonNode resposta = resultado(execucao);
        JsonNode leitura = resposta.get("leitura");
        assertThat(leitura.get("documentosLidos").asInt()).isEqualTo(1);
        assertThat(leitura.get("arquivosIlegiveis").asInt()).isEqualTo(2);
        List<String> listados = new ArrayList<>();
        leitura.get("arquivosQueNaoForamLidos").forEach(arquivo -> listados.add(arquivo.get("origem").asText()));
        assertThat(listados).containsExactlyInAnyOrder(NOME_DO_PRIMEIRO_CORROMPIDO, NOME_DO_SEGUNDO_CORROMPIDO);
        assertThat(leitura.get("comoFoiALeitura").asText())
                .contains("2 arquivo(s) não puderam ser lidos")
                .doesNotContain(NADA_FALHOU);

        // Arquivo ilegível nunca vira nota sem divergência: as quatro contagens somam só os produtos lidos.
        assertThat(somaDosQuatroEstados(resposta.get("conferencia")))
                .isEqualTo(resposta.get("conferencia").get("quantidadeDeProdutos").asInt());
        assertThat(quantidadeSemDivergencia(resposta.get("conferencia")))
                .isLessThanOrEqualTo(resposta.get("conferencia").get("quantidadeDeProdutos").asInt());

        // 4. Planilha: a contagem ao lado de "Documentos auditados", e os dois nomes listados.
        try (Workbook planilha = planilhaDe(execucao)) {
            Sheet resumo = planilha.getSheet("Resumo");
            assertThat(valorAoLadoDe(resumo, "Documentos auditados")).isEqualTo("1");
            assertThat(valorAoLadoDe(resumo, ROTULO_DOS_NAO_LIDOS)).isEqualTo("2");
            assertThat(textoDaAba(planilha.getSheet("Não lidos")))
                    .contains(NOME_DO_PRIMEIRO_CORROMPIDO)
                    .contains(NOME_DO_SEGUNDO_CORROMPIDO);
            assertThat(textoDaPlanilha(planilha)).doesNotContain(NADA_FALHOU);
        }
    }

    // "Nenhum arquivo deixou de ser lido" só pode sair quando é verdade: lote limpo, leitura registrada.
    @Test
    void loteSemFalhaDeveDizerQueNenhumArquivoDeixouDeSerLido() throws IOException {
        Path lote = Files.createDirectories(pasta.resolve("limpo"));
        copiar(DOCUMENTO_BOM, lote.resolve(NOME_DO_BOM));

        comando.executar(Argumentos.de("auditar", "--origem=" + lote));
        UUID execucao = unicaExecucao();

        JsonNode leitura = resultado(execucao).get("leitura");
        assertThat(leitura.get("arquivosIlegiveis").asInt()).isZero();
        assertThat(leitura.get("arquivosQueNaoForamLidos")).isEmpty();
        assertThat(leitura.get("comoFoiALeitura").asText()).contains(NADA_FALHOU);

        try (Workbook planilha = planilhaDe(execucao)) {
            assertThat(valorAoLadoDe(planilha.getSheet("Resumo"), ROTULO_DOS_NAO_LIDOS)).isEqualTo("0");
        }
    }

    // Execução gravada sem o registro da leitura — a CLI antes desta correção, ou o serviço chamado direto — não sabe se algo falhou, e diz isso; não afirma zero.
    @Test
    void execucaoSemLeituraRegistradaNaoDeveAfirmarQueNadaFalhou() throws IOException {
        Path lote = loteComUmBomEDoisCorrompidos();

        auditoriaSemRegistroDaLeitura.auditar(lote);
        UUID execucao = unicaExecucao();

        JsonNode leitura = resultado(execucao).get("leitura");
        assertThat(leitura.get("arquivosIlegiveis").isNull()).as("contagem desconhecida não é zero").isTrue();
        assertThat(leitura.get("arquivosQueNaoForamLidos").isNull()).isTrue();
        assertThat(leitura.get("motivoDosArquivosIlegiveisAusentes").asText()).isNotBlank();
        assertThat(leitura.get("comoFoiALeitura").asText()).doesNotContain(NADA_FALHOU);

        try (Workbook planilha = planilhaDe(execucao)) {
            assertThat(valorAoLadoDe(planilha.getSheet("Resumo"), ROTULO_DOS_NAO_LIDOS))
                    .startsWith("(não registrado");
            assertThat(textoDaPlanilha(planilha)).doesNotContain(NADA_FALHOU);
        }
    }

    // O registro de falhas da fonte da CLI é do processo: a segunda auditoria não pode gravar as falhas da primeira.
    @Test
    void segundaAuditoriaNoMesmoProcessoNaoDeveHerdarAsFalhasDaPrimeira() throws IOException {
        comando.executar(Argumentos.de("auditar", "--origem=" + loteComUmBomEDoisCorrompidos()));
        Path limpo = Files.createDirectories(pasta.resolve("limpo"));
        copiar(DOCUMENTO_BOM, limpo.resolve(NOME_DO_BOM));
        saida.limpar();

        comando.executar(Argumentos.de("auditar", "--origem=" + limpo));

        UUID segunda = jdbc.queryForObject(
                "select id from execucao_auditoria order by data_hora desc limit 1", UUID.class);
        assertThat(acervo.arquivosIlegiveis(segunda)).hasValue(List.of());
        assertThat(saida.texto()).contains(NADA_FALHOU);
    }

    // Análise da interface gravada antes da V20: sem a marca, mas com itens e falhas da mesma transação. A lista dela continua valendo.
    @Test
    void analiseDaInterfaceAnteriorAMarcaDeveContinuarComALista() throws IOException {
        UUID execucao = analiseDaInterface.analisar(loteComUmBomEDoisCorrompidos()).auditoria().execucao().id();
        jdbc.update("delete from leitura_da_execucao where execucao_id = ?", execucao);

        assertThat(acervo.arquivosIlegiveis(execucao).orElseThrow())
                .extracting(ArquivoIlegivel::origem)
                .containsExactlyInAnyOrder(NOME_DO_PRIMEIRO_CORROMPIDO, NOME_DO_SEGUNDO_CORROMPIDO);
    }

    // A marca diz quantas linhas há; se o banco foi mexido por fora, nenhum dos dois números é afirmado.
    @Test
    void marcaQueNaoBateComAsLinhasDeveSerRecusada() throws IOException {
        comando.executar(Argumentos.de("auditar", "--origem=" + loteComUmBomEDoisCorrompidos()));
        UUID execucao = unicaExecucao();
        jdbc.update("delete from falha_de_leitura_da_execucao where execucao_id = ? and ordem = 0", execucao);

        assertThatThrownBy(() -> acervo.arquivosIlegiveis(execucao))
                .isInstanceOf(AnaliseInvalida.class)
                .hasMessageContaining("diz 2")
                .hasMessageContaining("há 1");
    }

    // Método auxiliar que monta o lote do cenário: uma nota boa e dois arquivos que não são XML legível.
    private Path loteComUmBomEDoisCorrompidos() throws IOException {
        Path lote = Files.createDirectories(pasta.resolve("lote"));
        copiar(DOCUMENTO_BOM, lote.resolve(NOME_DO_BOM));
        copiar(DOCUMENTO_CORROMPIDO, lote.resolve(NOME_DO_PRIMEIRO_CORROMPIDO));
        Files.writeString(lote.resolve(NOME_DO_SEGUNDO_CORROMPIDO),
                "<nfeProc>conteudo ficticio interrompido", StandardCharsets.UTF_8);
        return lote;
    }

    private void copiar(String recurso, Path destino) throws IOException {
        try (InputStream entrada = getClass().getResourceAsStream(recurso)) {
            assertThat(entrada).as("recurso %s", recurso).isNotNull();
            Files.copy(entrada, destino);
        }
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

    private Workbook planilhaDe(UUID execucao) throws IOException {
        Path destino = pasta.resolve("papel-" + execucao + ".xlsx");
        exportacao.exportar(execucao, destino);
        return new XSSFWorkbook(Files.newInputStream(destino));
    }

    private static int somaDosQuatroEstados(JsonNode conferencia) {
        int soma = 0;
        for (JsonNode contagem : conferencia.get("produtosPorSituacao")) {
            soma += contagem.get("quantidade").asInt();
        }
        return soma;
    }

    private static int quantidadeSemDivergencia(JsonNode conferencia) {
        for (JsonNode contagem : conferencia.get("produtosPorSituacao")) {
            if (contagem.get("estado").asText().equals("SEM_DIVERGENCIA_IDENTIFICADA")) {
                return contagem.get("quantidade").asInt();
            }
        }
        throw new AssertionError("a conferência não traz a contagem de sem divergência");
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

    private static String textoDaPlanilha(Workbook planilha) {
        StringBuilder texto = new StringBuilder();
        for (Sheet aba : planilha) {
            texto.append(textoDaAba(aba));
        }
        return texto.toString();
    }

    // Abre a sessão pelo caminho do navegador, como a SessaoDeTeste do pacote da API, que não é visível daqui.
    private void entrar() {
        if (usuarios.listar().stream().noneMatch(usuario -> usuario.login().equals(LOGIN))) {
            usuarios.criar(LOGIN, "Usuário de teste das falhas de leitura", Perfil.ADMINISTRADOR,
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
                "carga-ficticia-d018",
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
