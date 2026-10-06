package br.edu.tcc.auditoria.infraestrutura.api;

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
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A medição de acurácia pela web: as notas e o gabarito enviados pela pessoa, o
 * mesmo serviço e o mesmo comparador do comando avaliar-acuracia.
 *
 * <p>A carga fictícia não traz alíquota, de modo que a regra de valor não tem
 * contra o que conferir e responde não avaliado; e admite só o CST {@code AAA},
 * enquanto a nota declara {@code 999}, de modo que alguma regra aponta. Qual
 * regra aponta é lido de uma análise feita antes, e não suposto aqui.</p>
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
class AcuraciaPelaApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO = "/documentos/nfe-item-completo.xml";
    private static final String CHAVE = "1".repeat(44);
    private static final String CABECALHO = "chave_documento;numero_item;regra_id;rotulo_esperado\n";

    private static final String TABELAS = String.join(", ",
            "correcao_de_analise", "autoria_da_execucao", "resumo_da_execucao",
            "achado_evidencia", "achado_da_execucao", "achado", "tratativa",
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
    private ServicoDeImportacaoDeCatalogo importacao;

    @Autowired
    private ServicoDeAnalise analises;

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
    void preparar() {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacao.importar(carga("carga-ficticia"));
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void aTelaDeveSaberAntesDeMedirQueACargaSeraSelada() {
        String previa = rest.getForEntity("/api/acuracia/previa", String.class).getBody();

        assertThat(previa)
                .contains("\"versaoDaCarga\":\"carga-ficticia\"")
                .contains("\"jaSelada\":false")
                .contains("Esta medição usará e selará a carga \\\"carga-ficticia\\\"");
    }

    @Test
    void medirDeveSelarACargaQueATelaMostrou() throws IOException {
        ResponseEntity<String> resposta = medir(CABECALHO + CHAVE + ";1;R01;CONFORME\n", "carga-ficticia");

        assertThat(resposta.getStatusCode().value()).as(resposta.getBody()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select selada_em is not null from carga_catalogo", Boolean.class)).isTrue();
    }

    @Test
    void seACargaMudouDepoisDaPreviaNadaEMedidoNemSelado() throws IOException {
        ResponseEntity<String> resposta = medir(CABECALHO + CHAVE + ";1;R01;CONFORME\n", "outra-carga");

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody()).contains("CARGA_MUDOU").contains("Nada foi medido e nada foi selado");
        assertThat(jdbc.queryForObject("select selada_em is null from carga_catalogo", Boolean.class)).isTrue();
    }

    @Test
    void todaLinhaDeMetricaDeveTrazerAsVersoesEACobertura() throws IOException {
        String corpo = medir(CABECALHO + CHAVE + ";1;R01;CONFORME\n", "carga-ficticia").getBody();

        // D021 (04/10/2026): a faixa de natureza também tem rótulos e a versão do catálogo; as linhas de métrica
        // passam a ser contadas pelo regraId, que só elas têm, e a versão do catálogo aparece uma vez a mais.
        int linhas = contar(corpo, "\"regraId\"");
        assertThat(linhas).as("sete regras e o consolidado").isEqualTo(8);
        assertThat(contar(corpo, "\"versaoDoCatalogo\":\"carga-ficticia\"")).isEqualTo(linhas + 2);
        assertThat(contar(corpo, "\"versaoDoConjuntoDeRegras\":\"2026.6\"")).isEqualTo(linhas + 1);
        assertThat(contar(corpo, "\"cobertura\":")).isEqualTo(linhas);
    }

    @Test
    void metricaIndefinidaSaiComoTextoENuncaComoZero() throws IOException {
        String corpo = medir(CABECALHO + CHAVE + ";1;R01;CONFORME\n", "carga-ficticia").getBody();
        String semLinhas = linhaDaRegra(corpo, "R07");

        assertThat(semLinhas)
                .as("regra sem nenhuma linha no gabarito: nada avaliado, nada a medir")
                .contains("\"precisao\":{\"texto\":\"(indefinida)\",\"valor\":null")
                .contains("\"cobertura\":{\"texto\":\"(indefinida)\",\"valor\":null")
                .doesNotContain("\"texto\":\"0");
    }

    @Test
    void itemNaoAvaliadoNoGabaritoNaoAlteraPrecisaoNemRecallEAparecemSoNaCobertura() throws IOException {
        String regraQueAponta = regraQueApontaNaNota();
        String base = CABECALHO
                + CHAVE + ";1;" + regraQueAponta + ";ACHADO\n"
                + CHAVE + ";1;R03;CONFORME\n"
                + CHAVE + ";1;R06;ACHADO\n";
        String comNaoAvaliado = base + CHAVE + ";1;R05;ACHADO\n";

        String sem = medir(base, "carga-ficticia").getBody();
        String com = medir(comNaoAvaliado, "carga-ficticia").getBody();

        assertThat(linhaDaRegra(com, "R05"))
                .as("a regra de valor não tinha alíquota contra o que conferir")
                .contains("\"naoAvaliados\":1")
                .contains("\"precisao\":{\"texto\":\"(indefinida)\"");
        assertThat(metrica(consolidado(com), "precisao")).isEqualTo(metrica(consolidado(sem), "precisao"));
        assertThat(metrica(consolidado(com), "recall")).isEqualTo(metrica(consolidado(sem), "recall"));
        assertThat(metrica(consolidado(com), "f1")).isEqualTo(metrica(consolidado(sem), "f1"));
        assertThat(metrica(consolidado(com), "cobertura"))
                .as("o não avaliado aparece só aqui")
                .isNotEqualTo(metrica(consolidado(sem), "cobertura"));
    }

    @Test
    void oConsolidadoDeveSeDizerMicroENaoHaMediaMacro() throws IOException {
        String corpo = medir(CABECALHO + CHAVE + ";1;R01;CONFORME\n", "carga-ficticia").getBody();

        assertThat(corpo)
                .contains("\"rotulo\":\"Consolidado (micro)\"")
                .contains("soma das células")
                .contains("Não há média macro")
                .doesNotContain("\"macro\"");
    }

    @Test
    void consultaNaoMedePorqueMedirSelaACarga() throws IOException {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.CONSULTA);

        ResponseEntity<String> resposta = medir(CABECALHO + CHAVE + ";1;R01;CONFORME\n", "carga-ficticia");

        assertThat(resposta.getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select selada_em is null from carga_catalogo", Boolean.class)).isTrue();
    }

    @Test
    void gabaritoMalformadoDeveDizerOQueCorrigir() throws IOException {
        ResponseEntity<String> resposta = medir(CABECALHO + CHAVE + ";1;R01;TALVEZ\n", "carga-ficticia");

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        // A recusa cita o valor e os aceitos. Não cita a linha: o leitor de gabarito da Etapa 7 não a acrescenta
        // a recusa do domínio, e isso está registrado como pendência na D014.
        assertThat(resposta.getBody()).contains("TALVEZ").contains("ACHADO, CONFORME");
    }

    /** Analisa a nota uma vez, pelo caminho normal, e lê qual regra apontou — em vez de supor. */
    private String regraQueApontaNaNota() throws IOException {
        Path pasta = Files.createTempDirectory("acuracia-");
        Files.write(pasta.resolve("nota.xml"), documento());
        analises.analisar(pasta);
        List<String> regras = jdbc.queryForList("select distinct regra_id from achado order by regra_id", String.class);
        assertThat(regras).as("a carga fictícia precisa produzir ao menos um apontamento").isNotEmpty();
        return regras.get(0);
    }

    private ResponseEntity<String> medir(String gabarito, String cargaEsperada) throws IOException {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("notas", arquivo("nota.xml", documento()));
        corpo.add("gabarito", arquivo("gabarito.csv", gabarito.getBytes(StandardCharsets.UTF_8)));
        corpo.add("cargaEsperada", cargaEsperada);
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/acuracia", new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private byte[] documento() throws IOException {
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO)) {
            return conteudo.readAllBytes();
        }
    }

    private static ByteArrayResource arquivo(String nome, byte[] conteudo) {
        return new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
    }

    private static String linhaDaRegra(String corpo, String regra) {
        int inicio = corpo.indexOf("\"regraId\":\"" + regra + "\"");
        assertThat(inicio).as("linha da regra %s", regra).isNotNegative();
        return corpo.substring(inicio, corpo.indexOf("}}", corpo.indexOf("\"cobertura\"", inicio)) + 2);
    }

    private static String consolidado(String corpo) {
        int inicio = corpo.indexOf("\"consolidado\"");
        return corpo.substring(inicio, corpo.indexOf("}}", corpo.indexOf("\"cobertura\"", inicio)) + 2);
    }

    private static String metrica(String linha, String nome) {
        Matcher achado = Pattern.compile("\"" + nome + "\":(\\{[^}]*\\})").matcher(linha);
        assertThat(achado.find()).as("métrica %s", nome).isTrue();
        return achado.group(1);
    }

    private static int contar(String texto, String trecho) {
        int vezes = 0;
        for (int posicao = texto.indexOf(trecho); posicao >= 0; posicao = texto.indexOf(trecho, posicao + 1)) {
            vezes++;
        }
        return vezes;
    }

    private static CargaDeCatalogo carga(String versao) {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("999999"), Set.of(new CodigoCst("AAA")),
                "DISPOSITIVO FICTICIO PARA TESTE", false, Optional.empty(), List.of(), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        return new CargaDeCatalogo(versao, new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO, classificacoes, ncms, List.of(), List.of()),
                classificacoes, ncms, List.of(), List.of());
    }
}
