package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os quatro desfechos da conferência, cada um de ponta a ponta.
 *
 * <h2>Uma nota só, quatro cargas</h2>
 *
 * <p>O documento é o mesmo nos quatro cenários. O que muda é o catálogo — e é
 * esse o ponto. A situação de um produto não é propriedade do XML: ela é o
 * resultado de confrontar o XML com uma base normativa carregada, numa data. Um
 * teste que trocasse o documento a cada cenário provaria menos, porque deixaria
 * em aberto qual dos dois lados produziu a diferença.</p>
 *
 * <h2>Como cada carga produz o desfecho dela</h2>
 *
 * <ul>
 *   <li><strong>Tratamento normal</strong> — o NCM não consta de anexo nenhum, o
 *       cClassTrib existe e admite o CST declarado, e os valores fecham com as
 *       alíquotas. As sete regras concluem sem violação.</li>
 *   <li><strong>Tratamento diferenciado</strong> — o NCM consta de anexo, e o
 *       cClassTrib declara redução: o tratamento foi aproveitado, e R04 nada tem
 *       a relatar. O desfecho é o mesmo do anterior, e a diferença aparece na
 *       fundamentação, que é onde ela deve aparecer.</li>
 *   <li><strong>Requer conferência</strong> — o NCM consta de anexo e o
 *       cClassTrib é de tributação integral: há tratamento possivelmente não
 *       aproveitado. R04 é INFORMATIVA, e informativa não é divergência.</li>
 *   <li><strong>Sem dados suficientes</strong> — a carga não traz alíquota
 *       nenhuma, e R05 não tem contra o que conferir valor. Não vira conforme.</li>
 * </ul>
 *
 * <h2>Todo valor aqui é fictício e assim declarado</h2>
 *
 * <p>NCM de oito zeros, código {@code 999999}, CST {@code 999}, anexo e
 * tratamento em caixa alta dizendo que são fictícios, vigência aberta a partir
 * de 1900, percentuais de dígitos repetidos. Nada nesta classe pode ser lido
 * como afirmação sobre a legislação, e as cargas são declaradas
 * {@code FICTICIO} — é a faixa de procedência que as telas exibem.</p>
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
@DisplayName("Os quatro desfechos, de ponta a ponta")
class QuatroCenariosDeConferenciaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CAMINHO = "/api/analises";
    private static final String DOCUMENTO = "/documentos/nfe-item-completo.xml";

    private static final String FONTE = "FONTE FICTICIA PARA TESTE v0.0";
    private static final String NCM_DECLARADO = "00000000";
    private static final String CODIGO_DECLARADO = "999999";
    private static final String CST_DECLARADO = "999";
    private static final String ANEXO = "ANEXO-XX-FICTICIO";
    private static final String TRATAMENTO_DO_ANEXO = "TRATAMENTO-DIFERENCIADO-FICTICIO";

    /*
     * Os percentuais existem para que a conta de R05 feche com o que o documento
     * declara: base 99,99 com 9,99% da 9,989001, e o XML declara 9,99 — dentro da
     * tolerancia de 0,01. Sao numeros de digitos repetidos, impossiveis de
     * confundir com aliquota real.
     */
    private static final BigDecimal IBS_UF = new BigDecimal("9.9900");
    private static final BigDecimal IBS_MUNICIPAL = new BigDecimal("8.8800");
    private static final BigDecimal CBS = new BigDecimal("7.7700");

    private static final String TABELAS = String.join(", ",
            "achado_evidencia", "achado", "tratativa",
            "item_da_execucao", "falha_de_leitura_da_execucao",
            "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra",
            "avaliacao_nao_concluida", "achado_da_execucao", "execucao_auditoria",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "cobertura_catalogo", "natureza_da_carga", "carga_catalogo");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacaoDeCatalogo;

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
    void limparTudo() {
        jdbc.execute("truncate table " + TABELAS + " cascade");
    }

    /* --- 1. tratamento normal ------------------------------------------------ */

    @Test
    void tratamentoNormalDeveConcluirSemDivergencia() throws IOException {
        importacaoDeCatalogo.importar(carga("carga-tratamento-normal",
                List.of(), integral()));

        String detalhe = analisarEAbrirODetalhe();

        assertThat(situacaoDoProduto(detalhe))
                .describedAs("as sete regras concluíram sem encontrar violação")
                .isEqualTo("SEM_DIVERGENCIA_IDENTIFICADA");
        assertThat(detalhe)
                .describedAs("e a tela nunca chama isso de conferido")
                .doesNotContain("Conferido");
        assertThat(detalhe)
                .describedAs("o NCM não consta de anexo, e o bloco diz isso em vez de ficar vazio")
                .contains("não vincula o NCM 00000000 a nenhum anexo vigente");
        assertThat(quantidadeDoEstado(detalhe, "SEM_DIVERGENCIA_IDENTIFICADA"))
                .describedAs("as sete verificações deste produto")
                .isEqualTo(7);
    }

    /* --- 2. tratamento diferenciado ------------------------------------------ */

    @Test
    void tratamentoDiferenciadoAproveitadoDeveConcluirSemDivergencia() throws IOException {
        importacaoDeCatalogo.importar(carga("carga-tratamento-diferenciado",
                List.of(anexo()), comReducao()));

        String detalhe = analisarEAbrirODetalhe();

        assertThat(situacaoDoProduto(detalhe))
                .describedAs("o tratamento do anexo foi aproveitado: R04 nada tem a relatar")
                .isEqualTo("SEM_DIVERGENCIA_IDENTIFICADA");
        assertThat(detalhe)
                .describedAs("a diferença aparece na fundamentação, e não no desfecho")
                .contains(ANEXO)
                .contains(TRATAMENTO_DO_ANEXO);
        assertThat(detalhe)
                .describedAs("a redução declarada sai como valor, e não como ausência")
                .contains("\"percentualReducao\":\"99.99\"")
                .contains("\"motivoSemReducao\":null");
    }

    /**
     * O mesmo documento, duas cargas, dois enquadramentos diferentes.
     *
     * <p>É a prova de que o tratamento exibido vem do catálogo, e não do XML: o
     * arquivo enviado é byte a byte o mesmo dos outros cenários.</p>
     */
    @Test
    void oMesmoDocumentoDeveMostrarEnquadramentosDiferentesEmCargasDiferentes()
            throws IOException {
        importacaoDeCatalogo.importar(carga("carga-sem-anexo", List.of(), integral()));
        String semAnexo = analisarEAbrirODetalhe();

        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(carga("carga-com-anexo", List.of(anexo()), comReducao()));
        String comAnexo = analisarEAbrirODetalhe();

        assertThat(semAnexo).doesNotContain(ANEXO);
        assertThat(comAnexo).contains(ANEXO);
        assertThat(situacaoDoProduto(semAnexo)).isEqualTo(situacaoDoProduto(comAnexo));
    }

    /* --- 3. requer conferência ----------------------------------------------- */

    @Test
    void tratamentoDeAnexoNaoAproveitadoDeveRequererConferencia() throws IOException {
        importacaoDeCatalogo.importar(carga("carga-requer-conferencia",
                List.of(anexo()), integral()));

        String detalhe = analisarEAbrirODetalhe();

        assertThat(situacaoDoProduto(detalhe))
                .describedAs("informativa não é divergência: a situação merece leitura de pessoa")
                .isEqualTo("REQUER_CONFERENCIA");
        assertThat(detalhe)
                .describedAs("e a regra que a produziu é a informativa")
                .contains("\"regraId\":\"R04\"");
        assertThat(quantidadeDoEstado(detalhe, "REQUER_CONFERENCIA")).isEqualTo(1);
        assertThat(quantidadeDoEstado(detalhe, "POSSIVEL_DIVERGENCIA"))
                .describedAs("nenhuma regra mais forte disparou; se tivesse, ela prevaleceria")
                .isZero();
    }

    /* --- 4. sem dados suficientes -------------------------------------------- */

    @Test
    void semAliquotaNoCatalogoNaoDeveSerPossivelConcluir() throws IOException {
        importacaoDeCatalogo.importar(carga("carga-sem-aliquota",
                List.of(), integral(), List.of()));

        String detalhe = analisarEAbrirODetalhe();

        assertThat(situacaoDoProduto(detalhe))
                .describedAs("falta de dado não vira conforme — é o defeito que o projeto corrigiu")
                .isEqualTo("NAO_FOI_POSSIVEL_CONCLUIR");
        assertThat(detalhe)
                .describedAs("e o motivo é o que a própria regra escreveu")
                .contains("O catálogo não traz alíquota vigente");
        assertThat(quantidadeDoEstado(detalhe, "NAO_FOI_POSSIVEL_CONCLUIR")).isEqualTo(1);
        assertThat(quantidadeDoEstado(detalhe, "SEM_DIVERGENCIA_IDENTIFICADA"))
                .describedAs("as outras seis concluíram, e a pendência não foi somada a elas")
                .isEqualTo(6);
    }

    /**
     * Os quatro desfechos, lado a lado, na mesma execução do teste.
     *
     * <p>Cada cenário isolado prova o seu. Este prova que os quatro são
     * <strong>distintos</strong>: se dois deles colapsassem — uma pendência
     * virando conforme, uma informativa virando divergência — os testes de cima
     * continuariam passando enquanto a distinção que dá sentido à etapa
     * desaparecia.</p>
     */
    @Test
    void osQuatroDesfechosDevemSerDistintos() throws IOException {
        String normal = comCarga("cenario-normal", List.of(), integral(), aliquotas());
        String diferenciado = comCarga("cenario-diferenciado", List.of(anexo()), comReducao(),
                aliquotas());
        String conferencia = comCarga("cenario-conferencia", List.of(anexo()), integral(),
                aliquotas());
        String semDados = comCarga("cenario-sem-dados", List.of(), integral(), List.of());

        assertThat(List.of(
                situacaoDoProduto(normal),
                situacaoDoProduto(diferenciado),
                situacaoDoProduto(conferencia),
                situacaoDoProduto(semDados)))
                .containsExactly(
                        "SEM_DIVERGENCIA_IDENTIFICADA",
                        "SEM_DIVERGENCIA_IDENTIFICADA",
                        "REQUER_CONFERENCIA",
                        "NAO_FOI_POSSIVEL_CONCLUIR");
    }

    /* --- apoio ---------------------------------------------------------------- */

    private String comCarga(
            String versao,
            List<ItemAnexo> anexos,
            ClassificacaoTributaria classificacao,
            List<AliquotaVigente> aliquotas) throws IOException {

        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(carga(versao, anexos, classificacao, aliquotas));
        return analisarEAbrirODetalhe();
    }

    /** Envia o documento e devolve o corpo do detalhe do primeiro produto. */
    private String analisarEAbrirODetalhe() throws IOException {
        ResponseEntity<String> criada = enviar();
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String id = campoDeTexto(criada.getBody(), "id");
        String produtos = rest.getForEntity(
                CAMINHO + "/" + id + "/produtos", String.class).getBody();
        assertThat(produtos).isNotNull();

        String detalhe = rest.getForEntity(
                CAMINHO + "/" + id + "/produtos/" + campoDeTexto(produtos, "endereco"),
                String.class).getBody();
        assertThat(detalhe).isNotNull();
        return detalhe;
    }

    private ResponseEntity<String> enviar() throws IOException {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add(ControladorDeAnalises.CAMPO_DO_ARQUIVO, new ByteArrayResource(recurso()) {
            @Override
            public String getFilename() {
                return "nota.xml";
            }
        });
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity(CAMINHO, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private byte[] recurso() throws IOException {
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO)) {
            if (conteudo == null) {
                throw new IllegalStateException("Fixture não encontrada: " + DOCUMENTO);
            }
            return conteudo.readAllBytes();
        }
    }

    /**
     * A situação do produto, lida do bloco "produto" do detalhe.
     *
     * <p>Recorta a partir de {@code "produto"} porque o corpo tem mais de um
     * campo chamado {@code situacao} — o do produto e o dos grupos de contagem.
     * Ler o primeiro que aparecer daria certo por acidente.</p>
     */
    private static String situacaoDoProduto(String json) {
        String recorte = json.substring(json.indexOf("\"produto\""));
        return campoDeTexto(recorte, "situacao");
    }

    /** A quantidade de um estado, dentro das contagens do produto. */
    private static int quantidadeDoEstado(String json, String estado) {
        String recorte = json.substring(json.indexOf("\"verificacoesPorEstado\""));
        var achado = Pattern.compile(
                        "\"estado\"\\s*:\\s*\"" + estado + "\".{0,400}?\"quantidade\"\\s*:\\s*([0-9]+)",
                        Pattern.DOTALL)
                .matcher(recorte);
        assertThat(achado.find()).describedAs("contagem do estado %s", estado).isTrue();
        return Integer.parseInt(achado.group(1));
    }

    private static String campoDeTexto(String json, String campo) {
        var achado = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(achado.find()).describedAs("campo \"%s\" na resposta", campo).isTrue();
        return achado.group(1);
    }

    /* --- as cargas ------------------------------------------------------------ */

    private static ProcedenciaNormativa procedencia() {
        return ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), FONTE);
    }

    /** Código de tributação integral: sem benefício e sem redução declarada. */
    private static ClassificacaoTributaria integral() {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CODIGO_DECLARADO),
                Set.of(new CodigoCst(CST_DECLARADO)),
                "DISPOSITIVO FICTICIO PARA TESTE",
                false,
                Optional.empty(),
                List.of(),
                procedencia());
    }

    /** O mesmo código, agora com redução declarada: o tratamento foi aplicado. */
    private static ClassificacaoTributaria comReducao() {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CODIGO_DECLARADO),
                Set.of(new CodigoCst(CST_DECLARADO)),
                "DISPOSITIVO FICTICIO PARA TESTE",
                false,
                Optional.of(new BigDecimal("99.99")),
                List.of(),
                procedencia());
    }

    private static ItemAnexo anexo() {
        return new ItemAnexo(
                new Ncm(NCM_DECLARADO),
                new IdentificadorAnexo(ANEXO),
                TRATAMENTO_DO_ANEXO,
                procedencia());
    }

    /** Uma alíquota por tributo: mais de uma faz R05 desistir de escolher. */
    private static List<AliquotaVigente> aliquotas() {
        return List.of(
                new AliquotaVigente(Tributo.IBS_UF, IBS_UF,
                        new Abrangencia("ABRANGENCIA-XX-FICTICIA"), procedencia()),
                new AliquotaVigente(Tributo.IBS_MUN, IBS_MUNICIPAL,
                        new Abrangencia("ABRANGENCIA-XX-FICTICIA"), procedencia()),
                new AliquotaVigente(Tributo.CBS, CBS,
                        new Abrangencia("ABRANGENCIA-XX-FICTICIA"), procedencia()));
    }

    private static CargaDeCatalogo carga(
            String versao, List<ItemAnexo> anexos, ClassificacaoTributaria classificacao) {
        return carga(versao, anexos, classificacao, aliquotas());
    }

    private static CargaDeCatalogo carga(
            String versao,
            List<ItemAnexo> anexos,
            ClassificacaoTributaria classificacao,
            List<AliquotaVigente> aliquotas) {

        ProcedenciaNormativa procedencia = procedencia();
        List<ClassificacaoTributaria> classificacoes = List.of(classificacao);
        List<RegistroNcm> ncms = List.of(new RegistroNcm(
                new Ncm(NCM_DECLARADO), "DESCRICAO FICTICIA DO NCM DE TESTE", procedencia));

        return new CargaDeCatalogo(
                versao,
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(
                        Natureza.FICTICIO, classificacoes, ncms, anexos, aliquotas),
                classificacoes,
                ncms,
                anexos,
                aliquotas);
    }
}
