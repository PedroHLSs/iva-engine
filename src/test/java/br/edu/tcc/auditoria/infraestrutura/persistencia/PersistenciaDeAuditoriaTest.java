package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.RepositorioTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistência de ponta a ponta contra um PostgreSQL de verdade, com as
 * migrations do Flyway aplicadas.
 *
 * <h2>O que este teste existe para provar</h2>
 *
 * <p>Que reprocessar o mesmo lote <strong>não duplica apontamento</strong> e
 * <strong>não perde tratativa</strong>. As duas coisas dependem de o apontamento
 * ser identificado pelo que aponta — resumo do item, regra e versão da regra — e
 * não pela linha em que foi gravado. Nada disso pode ser verificado com dublê de
 * banco: é o índice único da tabela e a ordem das operações que sustentam a
 * garantia.</p>
 *
 * <h2>Sem Docker, o teste se desabilita</h2>
 *
 * <p>Em vez de falhar. Quem clona o projeto para ler o código não é obrigado a
 * ter Docker; quem vai mexer na persistência é.</p>
 *
 * <h2>Os dados são fictícios</h2>
 *
 * <p>Código {@code 999999}, CST {@code AAA}, NCM {@code 00000000} e vigência
 * aberta a partir de 1900. Nada aqui afirma coisa alguma sobre a legislação — e
 * a vigência começa em 1900 justamente para que ninguém possa ler estes valores
 * como data real de vigência.</p>
 */
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class PersistenciaDeAuditoriaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO_DE_TESTE = "/documentos/nfe-item-completo.xml";
    private static final String FONTE_FICTICIA = "FONTE FICTICIA PARA TESTE v0.0";
    private static final String CODIGO_FICTICIO = "999999";
    private static final String CST_FICTICIO_ADMITIDO = "AAA";
    private static final String NCM_FICTICIO = "00000000";
    private static final String JUSTIFICATIVA =
            "Justificativa ficticia de teste: o item foi conferido manualmente.";

    /** Nomes das tabelas na ordem em que aparecem nas migrations, para limpeza. */
    private static final String TABELAS = String.join(", ",
            "achado_evidencia", "achado", "tratativa",
            "item_documento", "documento",
            "execucao_achado_por_severidade", "execucao_achado_por_regra", "execucao_auditoria",
            "classificacao_tributaria_cst", "classificacao_tributaria_campo_obrigatorio",
            "classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
            "cobertura_catalogo", "carga_catalogo");

    @TempDir
    private Path lote;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacaoDeCatalogo;

    @Autowired
    private ServicoDeAuditoria auditoria;

    @Autowired
    private ServicoDeTratativa tratativas;

    @Autowired
    private ConsultaDeAchados consulta;

    @Autowired
    private RepositorioTratativa repositorioDeTratativas;

    @Autowired
    private AchadoJpa achados;

    @Autowired
    private ExecucaoAuditoriaJpa execucoes;

    @Autowired
    private DocumentoJpa documentos;

    @Autowired
    private ItemDocumentoJpa itens;

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
    void prepararBancoELote() throws IOException {
        jdbc.execute("truncate table " + TABELAS + " cascade");
        importacaoDeCatalogo.importar(catalogoFicticio("carga-ficticia"));
        copiarDocumentoParaOLote();
    }

    @Test
    void deveGravarAExecucaoComOResumoDaEntradaEAsContagens() {
        ResultadoDaAuditoria resultado = auditoria.auditar(lote);
        ExecucaoAuditoria execucao = resultado.execucao();

        assertThat(execucoes.count()).isEqualTo(1);
        assertThat(execucao.hashEntrada()).matches("[0-9a-f]{64}");
        assertThat(execucao.versaoCatalogo()).isEqualTo("carga-ficticia");
        assertThat(execucao.versaoConjuntoRegras()).isNotBlank();
        assertThat(execucao.quantidadeDocumentos()).isEqualTo(1);
        assertThat(execucao.quantidadeItens()).isEqualTo(1);
        assertThat(execucao.quantidadeDeAchados()).isEqualTo(resultado.achados().size());
    }

    @Test
    void deveGravarOsApontamentosComSuasEvidencias() {
        auditoria.auditar(lote);

        List<AchadoRegistrado> listados = consulta.listar(FiltroDeAchados.tudo());

        assertThat(listados)
                .as("o CST declarado no documento não consta entre os que a classificação admite")
                .hasSize(1);
        assertThat(listados.get(0).achado().evidencias())
                .as("apontamento sem evidência não é conferível")
                .isNotEmpty();
        assertThat(listados.get(0).aberto()).isTrue();
    }

    @Test
    void naoDeveDuplicarAchadoAoReprocessarOMesmoLote() {
        ResultadoDaAuditoria primeira = auditoria.auditar(lote);
        long apontamentosDaPrimeiraRodada = achados.count();

        ResultadoDaAuditoria segunda = auditoria.auditar(lote);

        assertThat(achados.count())
                .as("o mesmo item, sob a mesma regra e na mesma versão, é o mesmo apontamento")
                .isEqualTo(apontamentosDaPrimeiraRodada);
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(itens.count()).isEqualTo(1);
        assertThat(execucoes.count())
                .as("cada rodada é um fato distinto e vira execução própria")
                .isEqualTo(2);
        assertThat(segunda.execucao().hashEntrada())
                .as("mesmo conjunto de arquivos, mesmo resumo de entrada")
                .isEqualTo(primeira.execucao().hashEntrada());
        assertThat(segunda.execucao().quantidadeDeAchados())
                .as("a contagem da execução é do que ela encontrou, não do que gravou de novo")
                .isEqualTo(primeira.execucao().quantidadeDeAchados());
    }

    @Test
    void deveManterATratativaAoReprocessarOMesmoLote() {
        auditoria.auditar(lote);
        AchadoRegistrado antes = consulta.listar(FiltroDeAchados.tudo()).get(0);
        tratativas.registrar(antes.id(), DecisaoDeTratativa.REFUTADO, JUSTIFICATIVA);

        auditoria.auditar(lote);

        AchadoRegistrado depois = consulta.listar(FiltroDeAchados.tudo()).get(0);
        assertThat(depois.tratado())
                .as("a decisão de uma pessoa não pode se perder porque o lote rodou de novo")
                .isTrue();
        assertThat(depois.tratativa().orElseThrow().decisao())
                .isEqualTo(DecisaoDeTratativa.REFUTADO);
        assertThat(depois.tratativa().orElseThrow().justificativa()).isEqualTo(JUSTIFICATIVA);
        assertThat(depois.id())
                .as("o apontamento é a mesma linha, e não uma cópia")
                .isEqualTo(antes.id());
    }

    @Test
    void deveOmitirDaListagemDeAbertosOApontamentoJaTratado() {
        auditoria.auditar(lote);
        AchadoRegistrado registrado = consulta.listar(FiltroDeAchados.tudo()).get(0);
        tratativas.registrar(registrado.id(), DecisaoDeTratativa.ACEITO, JUSTIFICATIVA);

        FiltroDeAchados apenasAbertos = new FiltroDeAchados(
                Optional.empty(), Optional.empty(), Optional.empty(), true,
                FiltroDeAchados.LIMITE_PADRAO);

        assertThat(consulta.listar(apenasAbertos)).isEmpty();
        assertThat(consulta.listar(FiltroDeAchados.tudo()))
                .as("apontamento tratado continua no relatório: auditoria que esconde o que foi "
                        + "levantado deixa de ser conferível")
                .hasSize(1);
    }

    @Test
    void deveReabrirOApontamentoQuandoAVersaoDaRegraMuda() {
        auditoria.auditar(lote);
        AchadoRegistrado registrado = consulta.listar(FiltroDeAchados.tudo()).get(0);

        // Tratativa dada contra outra versão da mesma regra, sobre o mesmo item.
        repositorioDeTratativas.salvar(new Tratativa(
                new ChaveDeTratativa(
                        registrado.hashDoItem(),
                        registrado.achado().regraId(),
                        registrado.achado().regraVersao() + "-anterior"),
                DecisaoDeTratativa.REFUTADO,
                JUSTIFICATIVA,
                Instant.now()));

        assertThat(consulta.listar(FiltroDeAchados.tudo()).get(0).aberto())
                .as("a justificativa respondeu a outro critério; critério novo é pergunta nova")
                .isTrue();
    }

    @Test
    void deveSubstituirATratativaAnteriorSobreOMesmoApontamento() {
        auditoria.auditar(lote);
        AchadoRegistrado registrado = consulta.listar(FiltroDeAchados.tudo()).get(0);

        tratativas.registrar(registrado.id(), DecisaoDeTratativa.ACEITO, JUSTIFICATIVA);
        tratativas.registrar(registrado.id(), DecisaoDeTratativa.REFUTADO, "Outra justificativa ficticia.");

        AchadoRegistrado depois = consulta.listar(FiltroDeAchados.tudo()).get(0);
        assertThat(depois.tratativa().orElseThrow().decisao()).isEqualTo(DecisaoDeTratativa.REFUTADO);
        assertThat(depois.tratativa().orElseThrow().justificativa())
                .isEqualTo("Outra justificativa ficticia.");
    }

    @Test
    void naoDeveGravarIdentificadorDeParticipanteEmTextoClaro() {
        auditoria.auditar(lote);

        String emitente = jdbc.queryForObject(
                "select emitente_pseudonimizado from documento", String.class);

        assertThat(emitente)
                .as("nenhum CNPJ, CPF, nome ou endereço satisfaz 64 caracteres hexadecimais")
                .matches("[0-9a-f]{64}");
    }

    @Test
    void deveResolverOCatalogoNaDataDeEmissaoDoDocumento() {
        ResultadoDaAuditoria resultado = auditoria.auditar(lote);

        assertThat(resultado.quantidadeDeNaoAvaliadas())
                .as("o catálogo fictício não traz alíquota, então a regra de valor não conclui — "
                        + "e isso é registrado como não avaliado, nunca como conformidade")
                .isPositive();
        assertThat(resultado.achados())
                .as("as demais regras encontraram o código e o NCM na data de emissão")
                .hasSize(1);
    }

    /**
     * Catálogo fictício que reconhece o código e o NCM do documento de teste, mas
     * admite para o código um CST diferente do declarado.
     */
    private static CargaDeCatalogo catalogoFicticio(String versao) {
        ProcedenciaNormativa procedencia =
                ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), FONTE_FICTICIA);

        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CODIGO_FICTICIO),
                Set.of(new CodigoCst(CST_FICTICIO_ADMITIDO)),
                "Dispositivo ficticio para teste",
                false,
                Optional.empty(),
                List.of(),
                procedencia);

        RegistroNcm registroNcm = new RegistroNcm(
                new Ncm(NCM_FICTICIO), "Descricao ficticia de teste", procedencia);

        return new CargaDeCatalogo(
                versao,
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                List.of(classificacao),
                List.of(registroNcm),
                List.of(),
                List.of());
    }

    private void copiarDocumentoParaOLote() throws IOException {
        try (InputStream conteudo = getClass().getResourceAsStream(DOCUMENTO_DE_TESTE)) {
            if (conteudo == null) {
                throw new IllegalStateException(
                        "Documento de teste não encontrado no classpath: " + DOCUMENTO_DE_TESTE);
            }
            Files.copy(conteudo, lote.resolve("documento.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
