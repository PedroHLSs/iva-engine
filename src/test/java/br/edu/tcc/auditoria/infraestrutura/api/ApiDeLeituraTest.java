package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato dos quatro endpoints, por HTTP de verdade e contra um PostgreSQL de
 * verdade.
 *
 * <p>Um teste por endpoint, mais os que guardam as duas propriedades que definem a
 * etapa: que {@code NAO_AVALIADO} aparece por extenso no JSON, e que os dois campos
 * sensíveis ficam de fora por padrão.</p>
 *
 * <h2>A resposta é conferida como texto</h2>
 *
 * <p>E não desserializada de volta nos DTOs. Desserializar mediria o Jackson contra
 * si mesmo: um campo que a serialização omitisse voltaria a existir como nulo no
 * objeto, e o teste passaria justamente no caso que ele existe para pegar — que é
 * campo omitido da resposta.</p>
 *
 * <p>As comparações são por expressão regular com espaço opcional em volta dos dois
 * pontos, e não por texto literal, para não amarrarem o contrato ao recuo do JSON.
 * A propriedade que importa é o campo existir com aquele valor, não a resposta vir
 * indentada.</p>
 *
 * <p>Sem Docker, desabilita em vez de falhar — mesma escolha de
 * {@code PersistenciaDeAuditoriaTest} e {@code PapelDeTrabalhoDePontaAPontaTest}.</p>
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
class ApiDeLeituraTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    /** A carga destes testes é inteiramente de demonstração, e a faixa diz isso. */
    private static final NaturezaDaCarga NATUREZA_FICTICIA = new NaturezaDaCarga(
            java.util.Optional.of(Natureza.FICTICIO),
            java.util.Optional.of(Natureza.FICTICIO),
            java.util.Optional.empty(),
            java.util.Optional.empty());
    private static final String DOCUMENTO_DE_TESTE = "/documentos/nfe-item-completo.xml";
    private static final String CHAVE_DO_DOCUMENTO = "1".repeat(44);
    private static final String FONTE_FICTICIA = "FONTE FICTICIA PARA TESTE v0.0";
    private static final String CODIGO_FICTICIO = "999999";
    private static final String CST_FICTICIO_ADMITIDO = "AAA";
    private static final String NCM_FICTICIO = "00000000";
    private static final String DISPOSITIVO_FICTICIO = "Dispositivo ficticio para teste";
    private static final String JUSTIFICATIVA = "Justificativa ficticia com CNPJ 00.000.000/0000-00.";

    private static final String TABELAS = String.join(", ",
            "achado_evidencia", "achado_da_execucao", "achado", "tratativa",
            "avaliacao_nao_concluida", "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "cobertura_catalogo", "carga_catalogo");

    @TempDir
    private Path pasta;

    @Autowired
    private TestRestTemplate cliente;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacaoDeCatalogo;

    @Autowired
    private ServicoDeAuditoria auditoria;

    @Autowired
    private ServicoDeTratativa tratativas;

    @Autowired
    private ConsultaDeAchados achados;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID execucaoId;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void prepararBancoELote() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(catalogoFicticio());

        Path lote = Files.createDirectories(pasta.resolve("lote"));
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO_DE_TESTE)) {
            if (conteudo == null) {
                throw new IllegalStateException(
                        "Documento de teste não encontrado no classpath: " + DOCUMENTO_DE_TESTE);
            }
            Files.copy(conteudo, lote.resolve("documento.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
        execucaoId = auditoria.auditar(lote).execucao().id();
    }

    @Test
    void deveListarAsExecucoesComOsTotaisPorSeveridade() {
        String corpo = obter("/api/execucoes");

        assertThat(corpo)
                .containsPattern(par("id", texto(execucaoId.toString())))
                .containsPattern(par("quantidadeDocumentos", "1"))
                .containsPattern(par("limite", "25"))
                .contains("\"versaoCatalogo\"", "\"versaoConjuntoRegras\"", "\"quantidadeItens\"");

        assertThat(corpo)
                .as("as quatro severidades saem sempre, inclusive as que ficaram em zero: severidade "
                        + "omitida obriga o leitor a adivinhar se não houve apontamento ou se ninguém "
                        + "olhou")
                .contains("\"CRITICA\"", "\"GRAVE\"", "\"MODERADA\"", "\"INFORMATIVA\"");
    }

    @Test
    void deveDetalharAExecucaoComContagemPorRegraEMotivosAgrupados() {
        String corpo = obter("/api/execucoes/" + execucaoId);

        assertThat(corpo).contains(
                "\"desfechos\"",
                "\"avaliacoesProduzidas\"",
                "\"porRegra\"",
                "\"motivosDoNaoAvaliado\"",
                "\"itensComAvaliacaoNaoConcluida\"");

        assertThat(corpo)
                .as("toda regra aplicada tem linha, inclusive as que não apontaram nada e as que "
                        + "concluíram tudo")
                .containsPattern(par("regraId", texto("R01")))
                .containsPattern(par("regraId", texto("R07")));
    }

    @Test
    void deveTrazerOsTresDesfechosSemColapsarNaoAvaliadoEmAusenciaDeAchado() {
        String corpo = obter("/api/execucoes/" + execucaoId);

        assertThat(corpo)
                .as("o não avaliado tem campo próprio e não se confunde com ausência de achado")
                .contains("\"naoAvaliado\"");
        assertThat(corpo)
                .as("o conforme não é omitido: omiti-lo faria o consumidor concluir por subtração que "
                        + "tudo o que não é achado nem não avaliado está conforme")
                .contains("\"conforme\"", "\"derivacao\"");
        assertThat(corpo)
                .as("e a conta que produziu o conforme vem escrita, porque esse número não está "
                        + "gravado em coluna nenhuma")
                .contains("quantidadeItens", "avaliacoesProduzidas");
    }

    @Test
    void deveEscreverNaoAvaliadoPorExtensoEmCadaLinhaDoJson() {
        String corpo = obter("/api/execucoes/" + execucaoId + "/nao-avaliados");

        assertThat(corpo)
                .as("é a propriedade central da etapa: o desfecho é textual em cada linha, e não "
                        + "posicional. Um recorte copiado desta resposta continua dizendo o que é.")
                .containsPattern(par("resultado", texto(ResultadoAvaliacao.NAO_AVALIADO.name())));
        assertThat(corpo)
                .as("o motivo é o texto que a própria regra escreveu ao desistir; sem ele a resposta "
                        + "afirma que algo não foi avaliado sem dizer o que faltou")
                .contains("\"motivo\"");
        assertThat(corpo).contains("\"totalDeElementos\"");
    }

    @Test
    void deveListarOsAchadosPaginadosComOFiltroAplicadoDeVolta() {
        String regraQueApontou = achados.listar(FiltroDeAchados.tudo()).get(0).achado().regraId();

        String corpo = obter("/api/execucoes/" + execucaoId
                + "/achados?regra=" + regraQueApontou + "&tamanho=10");

        assertThat(corpo)
                .containsPattern(par("resultado", texto(ResultadoAvaliacao.ACHADO.name())))
                .containsPattern(par("regraId", texto(regraQueApontou)))
                .containsPattern(par("fundamentoNormativo", texto(DISPOSITIVO_FICTICIO)))
                .contains("\"evidencias\"", "\"vigenciaAplicada\"", "\"severidade\"");

        assertThat(corpo)
                .as("o filtro volta escrito, para ninguém confundir \"nada atende\" com \"ninguém "
                        + "procurou\"")
                .contains("\"filtro\"");
        assertThat(corpo)
                .as("e o total antes do recorte vai na resposta, para uma listagem curta não parecer "
                        + "um acervo limpo")
                .containsPattern(par("tamanho", "10"))
                .contains("\"totalDeElementos\"");
    }

    @Test
    void naoDeveExporAChaveDeAcessoNemAJustificativaPorPadrao() {
        AchadoRegistrado registrado = achados.listar(FiltroDeAchados.tudo()).get(0);
        tratativas.registrar(registrado.id(), DecisaoDeTratativa.ACEITO, JUSTIFICATIVA);

        String corpo = obter("/api/execucoes/" + execucaoId + "/achados");

        assertThat(corpo)
                .as("os dígitos intermediários da chave são o CNPJ do emitente")
                .doesNotContain(CHAVE_DO_DOCUMENTO);
        assertThat(corpo)
                .as("justificativa é texto livre humano e pode conter CNPJ ou razão social")
                .doesNotContain(JUSTIFICATIVA);

        assertThat(corpo)
                .as("mas nenhuma das duas é omitida em silêncio: sai null com o motivo ao lado")
                .containsPattern(par("chaveAcesso", "null"))
                .containsPattern(par("justificativa", "null"))
                .contains("\"motivoDaChaveOmitida\"", "\"motivoDaJustificativaOmitida\"");

        assertThat(corpo)
                .as("e o pseudônimo continua identificando o documento entre respostas")
                .contains("\"pseudonimo\"")
                .containsPattern(par("statusDeTratativa", texto("ACEITO")));
    }

    @Test
    void deveRecusarExecucaoInexistenteCom404() {
        ResponseEntity<String> resposta =
                cliente.getForEntity("/api/execucoes/" + UUID.randomUUID(), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody())
                .contains("EXECUCAO_NAO_ENCONTRADA")
                .contains("Não há execução gravada");
    }

    @Test
    void deveRecusarSeveridadeDesconhecidaDizendoOsValoresAceitos() {
        ResponseEntity<String> resposta = cliente.getForEntity(
                "/api/execucoes/" + execucaoId + "/achados?severidade=URGENTISSIMA", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody())
                .as("recusar sem dizer o que é aceito obriga quem integra a adivinhar")
                .contains("PEDIDO_INVALIDO")
                .contains("CRITICA");
    }

    private String obter(String caminho) {
        ResponseEntity<String> resposta = cliente.getForEntity(caminho, String.class);
        assertThat(resposta.getStatusCode())
                .as("resposta de %s: %s", caminho, resposta.getBody())
                .isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    /** Campo e valor, com espaço opcional em volta dos dois pontos. */
    private static Pattern par(String campo, String valorEmRegex) {
        return Pattern.compile("\"" + campo + "\"\\s*:\\s*" + valorEmRegex);
    }

    /** Um valor de texto JSON, tomado ao pé da letra dentro da expressão. */
    private static String texto(String valor) {
        return "\"" + Pattern.quote(valor) + "\"";
    }

    /** Catálogo fictício que reconhece o código e o NCM, mas admite outro CST. */
    private static CargaDeCatalogo catalogoFicticio() {
        ProcedenciaNormativa procedencia =
                ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), FONTE_FICTICIA);

        return new CargaDeCatalogo(
                "carga-ficticia",
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NATUREZA_FICTICIA,
                List.of(new ClassificacaoTributaria(
                        new CodigoClassificacaoTributaria(CODIGO_FICTICIO),
                        Set.of(new CodigoCst(CST_FICTICIO_ADMITIDO)),
                        DISPOSITIVO_FICTICIO,
                        false,
                        Optional.empty(),
                        List.of(),
                        procedencia)),
                List.of(new RegistroNcm(
                        new Ncm(NCM_FICTICIO), "Descricao ficticia de teste", procedencia)),
                List.of(),
                List.of());
    }
}
