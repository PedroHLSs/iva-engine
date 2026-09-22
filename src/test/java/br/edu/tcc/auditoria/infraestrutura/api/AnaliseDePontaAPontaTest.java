package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O caminho inteiro: arquivo entra por HTTP, o pipeline da Etapa 4 lê, o motor
 * roda, a Etapa 5 grava, e o recibo volta.
 *
 * <p>O cenário que dá nome à etapa está em
 * {@link #deveConcluirOLoteContandoOIlegivelSeparadoDoQueFoiLido()}: um pacote
 * com documentos bons e um ilegível, provando que o lote termina e que o
 * ilegível é contado à parte — nunca como nota sem divergência.</p>
 *
 * <p>A resposta é conferida como texto, pelo mesmo motivo da
 * {@code ApiDeLeituraTest}: desserializar de volta nos DTOs mediria o Jackson
 * contra si mesmo, e um campo omitido voltaria a existir como nulo.</p>
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
class AnaliseDePontaAPontaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CAMINHO = "/api/analises";

    private static final String DOCUMENTO_BOM = "/documentos/nfe-item-completo.xml";
    private static final String OUTRO_DOCUMENTO_BOM = "/documentos/nfe-multiplos-itens.xml";
    private static final String DOCUMENTO_ILEGIVEL = "/documentos/documento-corrompido.xml";

    private static final String FONTE_FICTICIA = "FONTE FICTICIA PARA TESTE v0.0";
    private static final String CODIGO_FICTICIO = "999999";
    private static final String CST_FICTICIO_ADMITIDO = "AAA";
    private static final String NCM_FICTICIO = "00000000";
    private static final String DISPOSITIVO_FICTICIO = "Dispositivo ficticio para teste";
    private static final String DISPOSITIVO_DA_CARGA_NOVA =
            "Dispositivo ficticio da carga posterior";

    private static final String TABELAS = String.join(", ",
            "achado_evidencia", "achado", "tratativa",
            "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
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
    void prepararBanco() {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(catalogoFicticio("carga-ficticia"));
    }

    @Test
    void deveAnalisarUmDocumentoAvulsoEDevolverORecibo() throws IOException {
        ResponseEntity<String> resposta = enviar("nota.xml", recurso(DOCUMENTO_BOM));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
        assertThat(resposta.getBody()).isNotNull();
        assertThat(campoNumerico(resposta.getBody(), "documentosLidos")).isEqualTo(1);
        assertThat(campoNumerico(resposta.getBody(), "arquivosIlegiveis")).isZero();
        assertThat(resposta.getBody()).contains("Nenhum arquivo deixou de ser lido");
    }

    /**
     * O cenário que a etapa pediu: pacote com documentos bons e um ilegível.
     *
     * <p>O lote conclui — os bons são lidos e auditados —, e o ilegível aparece
     * numa contagem própria, com o motivo, sem virar nota de espécie nenhuma.</p>
     */
    @Test
    void deveConcluirOLoteContandoOIlegivelSeparadoDoQueFoiLido() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("2026-01/nota-boa.xml", recurso(DOCUMENTO_BOM));
        entradas.put("2026-01/nota-boa-2.xml", recurso(OUTRO_DOCUMENTO_BOM));
        entradas.put("2026-01/nota-ilegivel.xml", recurso(DOCUMENTO_ILEGIVEL));

        ResponseEntity<String> resposta = enviar("acervo.zip", zipCom(entradas));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String corpo = resposta.getBody();
        assertThat(corpo).isNotNull();

        assertThat(campoNumerico(corpo, "documentosLidos"))
                .describedAs("um arquivo ruim não pode derrubar o lote")
                .isEqualTo(2);
        assertThat(campoNumerico(corpo, "arquivosIlegiveis"))
                .describedAs("e o que não pôde ser lido é contado à parte")
                .isEqualTo(1);
        assertThat(corpo)
                .describedAs("o ilegível vem nomeado e com motivo, não só contado")
                .contains("nota-ilegivel.xml")
                .contains("arquivosQueNaoForamLidos");
        assertThat(corpo).contains("não entram em contagem nenhuma de conferência");
    }

    @Test
    void deveRegistrarATentativaQuandoNenhumDocumentoPodeSerLido() throws IOException {
        ResponseEntity<String> resposta = enviar(
                "so-ruim.zip", zipCom(Map.of("ruim.xml", recurso(DOCUMENTO_ILEGIVEL))));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(campoNumerico(resposta.getBody(), "documentosLidos")).isZero();
        assertThat(campoNumerico(resposta.getBody(), "arquivosIlegiveis")).isEqualTo(1);
        assertThat(resposta.getBody())
                .describedAs("falhar a requisição apagaria o registro de que houve tentativa")
                .contains("Nenhum documento pôde ser lido");
    }

    /**
     * A propriedade que o servidor tem e a CLI não precisava ter.
     *
     * <p>O registro de falhas da Etapa 4 é um objeto só, vivo enquanto o processo
     * vive. Compartilhá-lo entre análises faria a segunda herdar os ilegíveis da
     * primeira — um lote impecável apareceria com falhas que não são dele.</p>
     */
    @Test
    void naoDeveHerdarArquivoIlegivelDaAnaliseAnterior() throws IOException {
        ResponseEntity<String> primeira = enviar(
                "ruim.zip", zipCom(Map.of("ruim.xml", recurso(DOCUMENTO_ILEGIVEL))));
        assertThat(campoNumerico(primeira.getBody(), "arquivosIlegiveis")).isEqualTo(1);

        ResponseEntity<String> segunda = enviar("boa.xml", recurso(DOCUMENTO_BOM));

        assertThat(campoNumerico(segunda.getBody(), "arquivosIlegiveis"))
                .describedAs("a falha da análise anterior não é desta")
                .isZero();
        assertThat(segunda.getBody()).contains("Nenhum arquivo deixou de ser lido");
    }

    @Test
    void deveReabrirOReciboPeloIdentificador() throws IOException {
        ResponseEntity<String> criada = enviar("nota.xml", recurso(DOCUMENTO_BOM));
        String id = campoDeTexto(criada.getBody(), "id");

        ResponseEntity<String> relida = rest.getForEntity(CAMINHO + "/" + id, String.class);

        assertThat(relida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(campoDeTexto(relida.getBody(), "id")).isEqualTo(id);
        assertThat(campoNumerico(relida.getBody(), "documentosLidos")).isEqualTo(1);
    }

    @Test
    void deveGuardarOArquivoIlegivelParaQuemReabrirODepois() throws IOException {
        ResponseEntity<String> criada = enviar(
                "acervo.zip", zipCom(Map.of(
                        "boa.xml", recurso(DOCUMENTO_BOM),
                        "ruim.xml", recurso(DOCUMENTO_ILEGIVEL))));
        String id = campoDeTexto(criada.getBody(), "id");

        ResponseEntity<String> relida = rest.getForEntity(CAMINHO + "/" + id, String.class);

        assertThat(campoNumerico(relida.getBody(), "arquivosIlegiveis"))
                .describedAs("em memória, isto sumiria no reinício e o lote pareceria completo")
                .isEqualTo(1);
        assertThat(relida.getBody()).contains("ruim.xml");
    }

    /**
     * A resposta separa leitura de conferência em dois blocos nomeados.
     *
     * <p>O bloco de leitura fala de arquivos e <strong>não tem campo de desfecho
     * nenhum</strong> — "zero apontamentos" ao lado de "10 documentos lidos"
     * seria lido como "está limpo". O bloco de conferência fala de produtos, e
     * traz os quatro estados juntos, que é a única forma em que eles significam
     * alguma coisa.</p>
     */
    @Test
    void aRespostaDeveSepararLeituraDeConferenciaEmBlocosNomeados() throws IOException {
        String corpo = enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo).contains("\"leitura\"").contains("\"conferencia\"");
        assertThat(corpo).contains("documentosLidos").contains("arquivosIlegiveis");
        assertThat(corpo).contains("produtosPorSituacao").contains("verificacoesPorEstado");
    }

    /** Os quatro estados aparecem sempre, inclusive os que ficaram em zero. */
    @Test
    void aConferenciaDeveTrazerOsQuatroEstadosInclusiveOsZeros() throws IOException {
        String corpo = enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody();

        assertThat(corpo).isNotNull();
        for (String estado : List.of(
                "POSSIVEL_DIVERGENCIA", "REQUER_CONFERENCIA",
                "NAO_FOI_POSSIVEL_CONCLUIR", "SEM_DIVERGENCIA_IDENTIFICADA")) {
            assertThat(corpo)
                    .describedAs("estado %s omitido da resposta", estado)
                    .contains(estado);
        }
        assertThat(corpo)
                .describedAs("o rótulo viaja junto do número, para a tela não inventar a frase")
                .contains("Não foi possível concluir")
                .contains("Sem divergência identificada");
    }

    /**
     * O terceiro número: produtos com pendência, contados produto a produto e não
     * pela situação, que a precedência esconderia.
     */
    @Test
    void aConferenciaDeveContarProdutosComPendenciaAlemDaSituacao() throws IOException {
        String corpo = enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo).contains("produtosComAlgumaVerificacaoNaoConcluida");
        assertThat(corpo).contains("comoFoiObtido");
    }

    @Test
    void deveListarOsProdutosComSituacaoEContagensProprias() throws IOException {
        String id = campoDeTexto(
                enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody(), "id");

        ResponseEntity<String> produtos =
                rest.getForEntity(CAMINHO + "/" + id + "/produtos", String.class);

        assertThat(produtos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(produtos.getBody()).isNotNull();
        assertThat(produtos.getBody())
                .describedAs("a situação nunca vem sozinha")
                .contains("rotuloDaSituacao")
                .contains("comoASituacaoFoiObtida")
                .contains("verificacoesPorEstado")
                .contains("verificacoes");
        assertThat(produtos.getBody())
                .describedAs("NCM e cClassTrib ausentes vêm com o campo irmão dizendo por quê")
                .contains("motivoDoNcmAusente")
                .contains("motivoDoClassTribAusente");
        assertThat(produtos.getBody())
                .describedAs("o produto é endereçado pelo resumo do item, não pela chave")
                .contains("endereco");
    }

    /**
     * O conforme é derivado, e a derivação diz que é derivada.
     *
     * <p>O banco não grava avaliação conforme, então a versão da regra dessas
     * verificações não existe em linha nenhuma. A resposta traz {@code null} com
     * o motivo ao lado, em vez de inventar a versão a partir do conjunto.</p>
     */
    @Test
    void aVerificacaoSemDivergenciaDeveExplicarPorQueNaoTemVersaoDeRegra() throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");

        String produtos = rest.getForEntity(CAMINHO + "/" + id + "/produtos", String.class).getBody();

        assertThat(produtos).isNotNull();
        assertThat(produtos)
                .contains("motivoDaVersaoAusente")
                .contains("O banco não grava avaliação conforme");
    }

    /**
     * O número, e não só a palavra.
     *
     * <p>O catálogo fictício desta suíte não traz alíquota nenhuma, então R05 não
     * tem como conferir valor e responde {@code NAO_AVALIADO} — que é
     * exatamente o desfecho que sumiria se a derivação do conforme engolisse as
     * pendências. Aqui o número é conferido, não a presença da palavra.</p>
     *
     * <p>E o teste mostra a precedência funcionando: no nível do produto a
     * pendência <strong>não</strong> aparece, porque este item também tem
     * divergência e é lá que ele é contado. Ela reaparece nos outros dois
     * números, que existem exatamente para isso.</p>
     */
    @Test
    void aPendenciaDeveAparecerComQuantidadeENaoSerEngolidaPeloConforme() throws IOException {
        String corpo = enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody();

        assertThat(corpo).isNotNull();

        // No nível do produto a precedência já agiu: este item tem divergência, e
        // é lá que ele é contado. A pendência dele não aparece aqui.
        assertThat(quantidadeDoEstado(corpo, "NAO_FOI_POSSIVEL_CONCLUIR", "produtosPorSituacao"))
                .isZero();

        // Mas ela não some do resultado, e é isso que os outros dois números
        // existem para garantir.
        assertThat(quantidadeDoEstado(corpo, "NAO_FOI_POSSIVEL_CONCLUIR", "verificacoesPorEstado"))
                .describedAs("R05 não tem alíquota no catálogo fictício e não pode virar conforme")
                .isPositive();
        assertThat(campoNumerico(corpo, "produtosComAlgumaVerificacaoNaoConcluida"))
                .describedAs("o produto tem pendência ainda que a situação dele seja divergência")
                .isPositive();
        assertThat(quantidadeDoEstado(corpo, "SEM_DIVERGENCIA_IDENTIFICADA", "verificacoesPorEstado"))
                .describedAs("e as que concluíram continuam sendo contadas")
                .isPositive();
    }

    /**
     * A soma dos quatro fecha com uma avaliação por par (item, regra).
     *
     * <p>É a invariante do motor sobre a qual a derivação do conforme se apoia.
     * Se ela deixar de valer, este teste acusa em vez de o número mudar de
     * significado em silêncio.</p>
     */
    @Test
    void osQuatroEstadosDevemSomarUmaVerificacaoPorParDeItemERegra() throws IOException {
        String corpo = enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody();

        assertThat(corpo).isNotNull();
        int soma = 0;
        for (String estado : List.of(
                "POSSIVEL_DIVERGENCIA", "REQUER_CONFERENCIA",
                "NAO_FOI_POSSIVEL_CONCLUIR", "SEM_DIVERGENCIA_IDENTIFICADA")) {
            soma += quantidadeDoEstado(corpo, estado, "verificacoesPorEstado");
        }
        int itens = campoNumerico(corpo, "itensLidos");
        assertThat(soma)
                .describedAs("três itens vezes sete regras")
                .isEqualTo(itens * 7);
    }

    @Test
    void deveRecusarRarPedindoZip() {
        ResponseEntity<String> resposta = enviar(
                "acervo.rar", "conteudo ficticio".getBytes(StandardCharsets.UTF_8));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("PACOTE_RECUSADO").contains(".zip");
    }

    @Test
    void deveRecusarPacoteSemNenhumDocumento() throws IOException {
        ResponseEntity<String> resposta = enviar(
                "acervo.zip", zipCom(Map.of("leiame.txt", "texto".getBytes(StandardCharsets.UTF_8))));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("análise de nada");
    }

    @Test
    void nenhumaRespostaDeveTrazerChaveDeAcessoEmTextoClaro() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("boa.xml", recurso(DOCUMENTO_BOM));
        entradas.put("ruim.xml", recurso(DOCUMENTO_ILEGIVEL));

        String corpo = enviar("acervo.zip", zipCom(entradas)).getBody();

        assertThat(corpo).isNotNull();
        assertThat(Pattern.compile("[0-9]{44}").matcher(corpo).find())
                .describedAs("os dígitos do meio da chave são o CNPJ do emitente")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // Detalhe do produto, agrupamento do lote e base tributaria.
    // Acrescentados na etapa de conferência, sobre os mesmos cenários acima.
    // -----------------------------------------------------------------------

    /**
     * A tela mais importante: IBS e CBS em blocos separados.
     *
     * <p>O catálogo fictício desta suíte não traz alíquota nenhuma, então os três
     * blocos saem com o motivo. É o caso que interessa conferir: o tributo não
     * some da tela por não haver dado — ele aparece dizendo que não há.</p>
     */
    @Test
    void oDetalheDeveTrazerIbsECbsEmBlocosSeparadosEComMotivoQuandoACargaNaoAlcanca()
            throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM);

        assertThat(corpo)
                .describedAs("os seis blocos da tela")
                .contains("\"documento\"")
                .contains("\"produto\"")
                .contains("\"declarado\"")
                .contains("\"tratamento\"")
                .contains("\"comparacao\"")
                .contains("\"passos\"");

        assertThat(corpo)
                .describedAs("os três tributos, sempre, com a família que agrupa IBS")
                .contains("IBS_UF")
                .contains("IBS_MUN")
                .contains("\"CBS\"")
                .contains("IBS - parcela estadual")
                .contains("IBS - parcela municipal");

        assertThat(corpo)
                .describedAs("sem alíquota na carga, o bloco diz por quê em vez de sumir")
                .contains("não traz alíquota deste tributo vigente em");
    }

    /** A fundamentação vem da carga, resolvida na data de emissão do documento. */
    @Test
    void oDetalheDeveFundamentarPelaCargaQueAAnaliseRegistrouENaDataDoDocumento()
            throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM);

        assertThat(corpo)
                .describedAs("as duas coordenadas andam juntas: carga e data")
                .contains("\"versaoDoCatalogo\":\"carga-ficticia\"")
                .contains("\"dataDeReferencia\":\"2026-01-15\"");
        assertThat(corpo)
                .describedAs("o dispositivo e a descrição vêm da carga, não do código")
                .contains("Dispositivo ficticio para teste")
                .contains("Descricao ficticia de teste")
                .contains(FONTE_FICTICIA);
        assertThat(corpo)
                .describedAs("vigência sem fim declarado se escreve, não se deixa em branco")
                .contains("motivoDaVigenciaSemFim");
    }

    /** Cada regra é explicada pela procedência dela, e não por texto decorado. */
    @Test
    void oDetalheDeveExplicarCadaRegraPelaProcedenciaDela() throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM);

        assertThat(corpo)
                .contains("\"tipo\":\"DERIVACAO\"")
                .contains("uma avaliação por par de item e regra")
                .contains("Não é estimativa");
        assertThat(corpo)
                .describedAs("o catálogo fictício não tem alíquota, então há pendência a explicar")
                .contains("\"tipo\":\"PENDENCIA\"");
    }

    @Test
    void oDetalheNuncaDeveDizerConferido() throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM).toLowerCase(java.util.Locale.ROOT);

        assertThat(corpo)
                .describedAs("o sistema não conferiu nada: aplicou as regras cadastradas")
                .doesNotContain("conferido")
                .doesNotContain("conferida");
    }

    @Test
    void oDetalheDeveCompararSemEmitirUmSegundoVeredito() throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM);

        assertThat(corpo)
                .contains("A conferência das regras sobre este produto está na situação acima");
        assertThat(corpo)
                .describedAs("célula em branco numa comparação é lida como igualdade")
                .contains("motivoDoNaoDeclarado");
    }

    @Test
    void todaRespostaDeResultadoDeveSairComOAvisoDeUso() throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");

        assertThat(rest.getForEntity(CAMINHO + "/" + id, String.class).getBody())
                .contains("não substitui a avaliação de profissional tributário");
        assertThat(rest.getForEntity(CAMINHO + "/" + id + "/produtos", String.class).getBody())
                .contains("não constitui parecer");
        assertThat(rest.getForEntity(CAMINHO + "/" + id + "/grupos", String.class).getBody())
                .contains("não constitui parecer");
    }

    @Test
    void deveRecusarDetalheDeProdutoQueNaoEDaAnalise() throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");

        ResponseEntity<String> resposta = rest.getForEntity(
                CAMINHO + "/" + id + "/produtos/" + "f".repeat(64), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody())
                .contains("PRODUTO_NAO_ENCONTRADO")
                .contains("o endereço é o resumo do item");
    }

    /** A tela do lote agrupa por cadastro, e diz o que a ordenação significa. */
    @Test
    void oLoteDeveAgruparPorCadastroEDeclararAOrdemEONivel() throws IOException {
        String id = campoDeTexto(
                enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody(), "id");

        ResponseEntity<String> grupos =
                rest.getForEntity(CAMINHO + "/" + id + "/grupos", String.class);

        assertThat(grupos.getStatusCode()).isEqualTo(HttpStatus.OK);
        String corpo = grupos.getBody();
        assertThat(corpo).isNotNull();

        assertThat(corpo)
                .describedAs("o valor sai com o rótulo grudado nele")
                .contains("valor dos produtos envolvidos");
        assertThat(corpo)
                .describedAs("a ordem padrão mede exposição, e precisa dizer isso")
                .contains("exposição, não gravidade")
                .contains("QUANTIDADE_DE_PRODUTOS");
        assertThat(corpo)
                .describedAs("o nível do agrupamento vai escrito, como na D010")
                .contains("rotuloDoNivel")
                .contains("agrupado por NCM e cClassTrib");
        assertThat(corpo)
                .describedAs("o item sem cClassTrib forma grupo próprio, com o nível dizendo isso")
                .contains("SOMENTE_NCM");
    }

    @Test
    void oResumoDoLoteDeveTrazerOsQuatroEstadosEOsIlegiveisSeparados() throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("boa.xml", recurso(OUTRO_DOCUMENTO_BOM));
        entradas.put("ruim.xml", recurso(DOCUMENTO_ILEGIVEL));
        String id = campoDeTexto(enviar("acervo.zip", zipCom(entradas)).getBody(), "id");

        String corpo = rest.getForEntity(CAMINHO + "/" + id + "/grupos", String.class).getBody();

        assertThat(corpo).isNotNull();
        assertThat(campoNumerico(corpo, "arquivosIlegiveis"))
                .describedAs("arquivo que não pôde ser lido é ausência, e aparece como tal")
                .isEqualTo(1);
        assertThat(corpo)
                .contains("POSSIVEL_DIVERGENCIA")
                .contains("REQUER_CONFERENCIA")
                .contains("NAO_FOI_POSSIVEL_CONCLUIR")
                .contains("SEM_DIVERGENCIA_IDENTIFICADA");
    }

    @Test
    void deveAbrirAsNotasEOsItensDeUmGrupo() throws IOException {
        String id = campoDeTexto(
                enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody(), "id");
        String grupos = rest.getForEntity(CAMINHO + "/" + id + "/grupos", String.class).getBody();
        assertThat(grupos).isNotNull();

        ResponseEntity<String> produtos = rest.getForEntity(
                CAMINHO + "/" + id + "/grupos/produtos?" + consultaDoPrimeiroGrupo(grupos),
                String.class);

        assertThat(produtos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(produtos.getBody())
                .describedAs("a página diz a que grupo ela pertence")
                .contains("\"grupo\"")
                .contains("valor dos produtos envolvidos");
        assertThat(produtos.getBody()).contains("endereco");
    }

    @Test
    void deveRecusarProdutosDeGrupoQueNaoExiste() throws IOException {
        String id = campoDeTexto(
                enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody(), "id");

        ResponseEntity<String> resposta = rest.getForEntity(
                CAMINHO + "/" + id + "/grupos/produtos?ncm=11111111&situacao=POSSIVEL_DIVERGENCIA",
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).contains("GRUPO_NAO_ENCONTRADO");
    }

    @Test
    void deveExigirASituacaoNaConsultaDeUmGrupo() throws IOException {
        String id = campoDeTexto(
                enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody(), "id");

        ResponseEntity<String> resposta = rest.getForEntity(
                CAMINHO + "/" + id + "/grupos/produtos?ncm=" + NCM_FICTICIO, String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("a situação faz parte da chave do grupo");
    }

    // ----- Base tributaria -------------------------------------------------

    @Test
    void aBaseTributariaDeveExigirDataEmVezDeCairEmHoje() {
        ResponseEntity<String> resposta =
                rest.getForEntity("/api/base-tributaria", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody())
                .describedAs("a D003 admitiu o caso de uso com data explícita, e só assim")
                .contains("é obrigatório, no formato aaaa-mm-dd");
    }

    @Test
    void aBaseTributariaDeveResponderNaDataPedidaEDizerDeQualCargaVeio() {
        ResponseEntity<String> resposta =
                rest.getForEntity("/api/base-tributaria?data=2026-01-15", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String corpo = resposta.getBody();
        assertThat(corpo).isNotNull();

        assertThat(corpo)
                .contains("\"data\":\"2026-01-15\"")
                .contains("\"versaoDoCatalogo\":\"carga-ficticia\"")
                .contains("\"cargaDisponivel\":true");
        assertThat(corpo)
                .describedAs("a cobertura declarada abre a tela, ou silêncio vira ausência")
                .contains("CLASSIFICACAO_TRIBUTARIA")
                .contains("ITEM_ANEXO");
        assertThat(corpo)
                .describedAs("os três tributos, mesmo sem alíquota na carga")
                .contains("IBS_UF")
                .contains("IBS_MUN")
                .contains("\"CBS\"");
        assertThat(corpo).contains("não lista as tabelas inteiras");
    }

    @Test
    void aBaseTributariaDeveDistinguirNaoPerguntadoDePerguntadoSemResposta() {
        String semPergunta =
                rest.getForEntity("/api/base-tributaria?data=2026-01-15", String.class).getBody();
        String comPergunta = rest.getForEntity(
                "/api/base-tributaria?data=2026-01-15&ncm=" + NCM_FICTICIO, String.class).getBody();

        assertThat(semPergunta).isNotNull();
        assertThat(comPergunta).isNotNull();

        assertThat(semPergunta)
                .describedAs("ninguém perguntou por NCM")
                .contains("\"ncmPerguntado\":null")
                .contains("\"ncmConsultado\":null");
        assertThat(comPergunta)
                .describedAs("perguntaram, e a carga responde")
                .contains("\"ncmPerguntado\":\"" + NCM_FICTICIO + "\"")
                .contains("Descricao ficticia de teste");
    }

    @Test
    void aBaseTributariaDeveDizerQuandoACargaNadaDizSobreOQueFoiPerguntado() {
        String corpo = rest.getForEntity(
                "/api/base-tributaria?data=2026-01-15&cClassTrib=000000", String.class).getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo)
                .contains("\"classTribPerguntado\":\"000000\"")
                .contains("não traz o cClassTrib 000000 vigente em 2026-01-15");
    }

    /**
     * O conflito que esta etapa precisou resolver para existir.
     *
     * <p>Reabrir uma análise de janeiro e fundamentá-la com o catálogo importado
     * em setembro mostraria, ao lado de apontamentos produzidos com uma tabela,
     * uma tabela que não os produziu — e a pessoa leria as duas coisas como se
     * fossem a mesma. É o que a D009 recusou ao tirar acurácia de dentro da
     * execução, e o que a D003 recusa ao exigir data.</p>
     *
     * <p>O cenário importa uma segunda carga <em>depois</em> da análise, com outro
     * dispositivo. Se o detalhe resolvesse pelo catálogo mais recente — que é o
     * comportamento de {@code ProvedorDeCatalogo}, e o certo para auditar —, ele
     * traria o dispositivo novo. Tem de trazer o antigo.</p>
     */
    @Test
    void oDetalheDeveFundamentarPelaCargaDaAnaliseAindaQueOutraTenhaSidoImportadaDepois()
            throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");

        importacaoDeCatalogo.importar(
                catalogoFicticio("carga-ficticia-2", DISPOSITIVO_DA_CARGA_NOVA));

        String produtos =
                rest.getForEntity(CAMINHO + "/" + id + "/produtos", String.class).getBody();
        assertThat(produtos).isNotNull();
        String corpo = rest.getForEntity(
                CAMINHO + "/" + id + "/produtos/" + campoDeTexto(produtos, "endereco"),
                String.class).getBody();
        assertThat(corpo).isNotNull();

        assertThat(corpo)
                .describedAs("a carga é a que a execução registrou, e vai escrita")
                .contains("\"versaoDoCatalogo\":\"carga-ficticia\"")
                .doesNotContain("carga-ficticia-2");
        assertThat(corpo)
                .describedAs("o dispositivo é o que valia para esta análise")
                .contains(DISPOSITIVO_FICTICIO)
                .doesNotContain(DISPOSITIVO_DA_CARGA_NOVA);
    }

    /**
     * A base tributária faz a pergunta oposta, e responde pela carga de agora.
     *
     * <p>São duas perguntas diferentes e por isso duas entradas diferentes. O que
     * não pode acontecer é uma responder no lugar da outra em silêncio — e é por
     * isso que as duas dizem, escrito, de qual carga vieram.</p>
     */
    @Test
    void aBaseTributariaDeveResponderPelaCargaMaisRecenteEDizerQualE() throws IOException {
        importacaoDeCatalogo.importar(
                catalogoFicticio("carga-ficticia-2", DISPOSITIVO_DA_CARGA_NOVA));

        String corpo = rest.getForEntity(
                "/api/base-tributaria?data=2026-01-15&cClassTrib=" + CODIGO_FICTICIO,
                String.class).getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo)
                .contains("\"versaoDoCatalogo\":\"carga-ficticia-2\"")
                .contains(DISPOSITIVO_DA_CARGA_NOVA);
    }

    // ----- Descrição do produto --------------------------------------------

    /**
     * A descrição chega ao banco, e a tela não a mostra por padrão.
     *
     * <p>As duas metades importam juntas. Se ela não chegasse ao banco, o
     * opt-in seria enfeite sobre um dado que não existe; se a tela a mostrasse por
     * padrão, o regime não seria opt-in.</p>
     */
    @Test
    void aDescricaoDeveSerGravadaEOmitidaDaRespostaPorPadrao() throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM);

        assertThat(gravadas())
                .describedAs("a descrição foi lida e gravada na linha do acervo")
                .containsExactly("PRODUTO FICTICIO DE TESTE");

        assertThat(corpo)
                .describedAs("e não sai por padrão: é texto livre do emitente, sem revisão")
                .doesNotContain("PRODUTO FICTICIO DE TESTE");
        assertThat(corpo)
                .contains("motivoSemDescricaoNaNota")
                .contains("não é exposta por esta instalação")
                .contains("auditoria.api.expor-descricao-do-produto");
    }

    /** A do catálogo aparece de qualquer jeito: ela veio de arquivo importado. */
    @Test
    void aDescricaoDoNcmDeveAparecerAoLadoAindaQueADaNotaEstejaOmitida() throws IOException {
        String corpo = detalheDoPrimeiroProduto(DOCUMENTO_BOM);

        assertThat(corpo)
                .contains("\"noCatalogo\":\"Descricao ficticia de teste\"")
                .contains("o sistema não as compara");
    }

    /**
     * O par descrição e item se mantém item a item.
     *
     * <p>Três itens, três descrições distintas, e cada linha do acervo com a sua.
     * Se o endereçamento fosse por outra coisa que não o resumo do item, este
     * teste mostraria descrição trocada de produto — que é o defeito pior, porque
     * a tela continua parecendo completa.</p>
     */
    @Test
    void cadaItemDeveGuardarADescricaoDele() throws IOException {
        enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM));

        assertThat(jdbc.queryForList(
                "select descricao_produto from item_da_execucao order by numero_item", String.class))
                .containsExactly(
                        "PRIMEIRO PRODUTO FICTICIO",
                        "SEGUNDO PRODUTO FICTICIO",
                        "TERCEIRO PRODUTO FICTICIO");
    }

    /** Duas análises seguidas, cada uma com as descrições que ela leu. */
    @Test
    void aSegundaAnaliseNaoDeveHerdarDescricaoDaPrimeira() throws IOException {
        String primeira = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");
        String segunda = campoDeTexto(
                enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM)).getBody(), "id");

        assertThat(descricoesDa(primeira)).containsExactly("PRODUTO FICTICIO DE TESTE");
        assertThat(descricoesDa(segunda))
                .describedAs("o acumulador de descrições é da análise, não do processo")
                .containsExactly(
                        "PRIMEIRO PRODUTO FICTICIO",
                        "SEGUNDO PRODUTO FICTICIO",
                        "TERCEIRO PRODUTO FICTICIO");
    }

    /** Toda linha do acervo diz o texto, ou diz por que não tem texto. */
    @Test
    void nenhumaLinhaDoAcervoPodeFicarMudaSobreADescricao() throws IOException {
        enviar("acervo.xml", recurso(OUTRO_DOCUMENTO_BOM));

        Integer mudas = jdbc.queryForObject(
                "select count(*) from item_da_execucao "
                        + "where descricao_produto is null and motivo_sem_descricao is null",
                Integer.class);

        assertThat(mudas)
                .describedAs("nulo nas duas colunas seria ausência sem dono")
                .isZero();
    }

    private List<String> gravadas() {
        return jdbc.queryForList(
                "select descricao_produto from item_da_execucao order by numero_item", String.class);
    }

    private List<String> descricoesDa(String execucaoId) {
        return jdbc.queryForList(
                "select descricao_produto from item_da_execucao where execucao_id = cast(? as uuid) "
                        + "order by numero_item",
                String.class,
                execucaoId);
    }

    // ----- Procedência da carga --------------------------------------------

    /**
     * A faixa aparece em toda tela de resultado, e não só onde o catálogo é exibido.
     *
     * <p>A situação de um produto foi produzida contra esta carga. Se ela é de
     * demonstração, o apontamento é de demonstração — e as telas que as pessoas
     * mais olham são justamente as que não mostram uma linha do catálogo.</p>
     */
    @Test
    void aFaixaDeProcedenciaDeveAparecerEmTodaRespostaDeResultado() throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");
        String produtos =
                rest.getForEntity(CAMINHO + "/" + id + "/produtos", String.class).getBody();
        assertThat(produtos).isNotNull();

        List<String> respostas = List.of(
                rest.getForEntity(CAMINHO + "/" + id, String.class).getBody(),
                produtos,
                rest.getForEntity(CAMINHO + "/" + id + "/grupos", String.class).getBody(),
                rest.getForEntity(
                        CAMINHO + "/" + id + "/produtos/" + campoDeTexto(produtos, "endereco"),
                        String.class).getBody(),
                rest.getForEntity("/api/base-tributaria?data=2026-01-15", String.class).getBody());

        for (String corpo : respostas) {
            assertThat(corpo)
                    .describedAs("dado de demonstração sem aviso é afirmação falsa sobre a lei")
                    .contains("INTEIRAMENTE_FICTICIO")
                    .contains("\"exigeAviso\":true")
                    .contains("Dados de demonstração");
        }
    }

    /** A faixa lista as tabelas, e não só diz que há dado fictício. */
    @Test
    void aFaixaDeveNomearAsTabelasDeDemonstracao() throws IOException {
        String corpo = enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo)
                .contains("\"tabelasFicticias\"")
                .contains("CLASSIFICACAO_TRIBUTARIA")
                .contains("NCM");
    }

    /** A procedência é gravada com a carga, e não deduzida na hora de exibir. */
    @Test
    void aProcedenciaDeveSerGravadaJuntoDaCarga() {
        assertThat(jdbc.queryForList(
                "select tabela || '=' || natureza from natureza_da_carga order by tabela",
                String.class))
                .describedAs("o catálogo desta suíte declara duas tabelas, as duas fictícias")
                .containsExactly("CLASSIFICACAO_TRIBUTARIA=FICTICIO", "NCM=FICTICIO");
    }

    /**
     * A faixa fala da carga que a análise registrou, e não da mais recente.
     *
     * <p>Mesma disciplina do tratamento: reabrir uma análise mostra a procedência
     * do catálogo que a produziu.</p>
     */
    @Test
    void aFaixaDeveFalarDaCargaDaAnaliseEnaoDaMaisRecente() throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(DOCUMENTO_BOM)).getBody(), "id");

        importacaoDeCatalogo.importar(
                catalogoFicticio("carga-ficticia-2", DISPOSITIVO_DA_CARGA_NOVA));

        String corpo = rest.getForEntity(CAMINHO + "/" + id, String.class).getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo)
                .contains("\"versaoDoCatalogo\":\"carga-ficticia\"")
                .doesNotContain("carga-ficticia-2");
    }

    private String detalheDoPrimeiroProduto(String documento) throws IOException {
        String id = campoDeTexto(enviar("nota.xml", recurso(documento)).getBody(), "id");
        String produtos =
                rest.getForEntity(CAMINHO + "/" + id + "/produtos", String.class).getBody();
        assertThat(produtos).isNotNull();

        String endereco = campoDeTexto(produtos, "endereco");
        ResponseEntity<String> detalhe = rest.getForEntity(
                CAMINHO + "/" + id + "/produtos/" + endereco, String.class);

        assertThat(detalhe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detalhe.getBody()).isNotNull();
        return detalhe.getBody();
    }

    /** A consulta que abre o primeiro grupo da lista, montada a partir dele. */
    private static String consultaDoPrimeiroGrupo(String grupos) {
        var achado = Pattern.compile(
                        "\"grupos\"\\s*:\\s*\\[\\s*\\{.*?\"ncm\"\\s*:\\s*(?:\"([^\"]*)\"|null)"
                                + ".*?\"cClassTrib\"\\s*:\\s*(?:\"([^\"]*)\"|null)"
                                + ".*?\"situacao\"\\s*:\\s*\"([^\"]+)\"",
                        Pattern.DOTALL)
                .matcher(grupos);
        assertThat(achado.find()).describedAs("primeiro grupo da lista").isTrue();

        StringBuilder consulta = new StringBuilder("situacao=").append(achado.group(3));
        if (achado.group(1) != null) {
            consulta.append("&ncm=").append(achado.group(1));
        }
        if (achado.group(2) != null) {
            consulta.append("&cClassTrib=").append(achado.group(2));
        }
        return consulta.toString();
    }

    private ResponseEntity<String> enviar(String nome, byte[] conteudo) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add(ControladorDeAnalises.CAMPO_DO_ARQUIVO, new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        });
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity(CAMINHO, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static byte[] zipCom(Map<String, byte[]> entradas) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private byte[] recurso(String caminho) throws IOException {
        try (InputStream conteudo = getClass().getResourceAsStream(caminho)) {
            if (conteudo == null) {
                throw new IllegalStateException("Fixture não encontrada no classpath: " + caminho);
            }
            return conteudo.readAllBytes();
        }
    }

    private static int campoNumerico(String json, String campo) {
        var achado = Pattern.compile("\"" + campo + "\"\\s*:\\s*(-?[0-9]+)").matcher(json);
        assertThat(achado.find()).describedAs("campo \"%s\" na resposta", campo).isTrue();
        return Integer.parseInt(achado.group(1));
    }

    /** A quantidade do estado dado, na primeira lista de contagens que aparecer. */
    private static int quantidadeDoEstado(String json, String estado) {
        return quantidadeDoEstado(json, estado, null);
    }

    /** A quantidade do estado dado, dentro do bloco nomeado. */
    private static int quantidadeDoEstado(String json, String estado, String bloco) {
        String recorte = bloco == null ? json : json.substring(json.indexOf("\"" + bloco + "\""));
        var achado = Pattern.compile(
                        "\"estado\"\s*:\s*\"" + estado + "\".{0,400}?\"quantidade\"\s*:\s*([0-9]+)",
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

    private static CargaDeCatalogo catalogoFicticio(String versao) {
        return catalogoFicticio(versao, DISPOSITIVO_FICTICIO);
    }

    private static CargaDeCatalogo catalogoFicticio(String versao, String dispositivo) {
        ProcedenciaNormativa procedencia =
                ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), FONTE_FICTICIA);

        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CODIGO_FICTICIO),
                Set.of(new CodigoCst(CST_FICTICIO_ADMITIDO)),
                dispositivo,
                false,
                Optional.empty(),
                List.of(),
                procedencia);

        RegistroNcm registroNcm = new RegistroNcm(
                new Ncm(NCM_FICTICIO), "Descricao ficticia de teste", procedencia);

        return new CargaDeCatalogo(
                versao,
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(
                        Natureza.FICTICIO,
                        List.of(classificacao),
                        List.of(registroNcm),
                        List.of(),
                        List.of()),
                List.of(classificacao),
                List.of(registroNcm),
                List.of(),
                List.of());
    }
}
