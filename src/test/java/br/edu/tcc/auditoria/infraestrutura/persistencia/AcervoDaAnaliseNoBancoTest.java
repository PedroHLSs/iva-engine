package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.analise.ItemDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.RegistroDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As duas tabelas da V7, contra um PostgreSQL de verdade.
 *
 * <p>O que precisa ficar provado aqui: que uma análise consegue recuperar os
 * produtos que leu — inclusive os que não produziram apontamento nenhum —, que
 * o arquivo ilegível sobrevive ao reinício, que a chave de acesso não entra na
 * coluna de origem nem por SQL direto, e que o {@code recomecar-do-zero}
 * continua limpando tudo sem ter sido alterado.</p>
 *
 * <p>Sem Docker, desabilita em vez de falhar — mesma escolha das outras suítes
 * de integração.</p>
 */
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class AcervoDaAnaliseNoBancoTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String DOCUMENTO_DE_TESTE = "/documentos/nfe-multiplos-itens.xml";
    private static final String FONTE_FICTICIA = "FONTE FICTICIA PARA TESTE v0.0";
    private static final String CODIGO_FICTICIO = "999999";
    private static final String CST_FICTICIO_ADMITIDO = "AAA";
    private static final String NCM_FICTICIO = "00000000";
    private static final String DESCRICAO_FICTICIA = "PRODUTO FICTICIO DE TESTE";

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
    private ServicoDeAuditoria auditoria;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacaoDeCatalogo;

    @Autowired
    private RegistroDoAcervoDaAnalise registro;

    @Autowired
    private ConsultaDoAcervoDaAnalise consulta;

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
    void deveRecuperarTodosOsItensQueAAnaliseLeuInclusiveOsSemApontamento() {
        ResultadoDaAuditoria resultado = auditoria.auditar(lote);
        UUID execucaoId = resultado.execucao().id();

        registro.registrar(execucaoId, itensDe(resultado), List.of());

        List<ItemDaAnalise> lidos = consulta.itensDaExecucao(execucaoId);
        assertThat(lidos)
                .describedAs("derivar a lista dos apontamentos devolveria só os itens com problema")
                .hasSize(resultado.execucao().quantidadeItens());
        assertThat(lidos)
                .describedAs("os três itens do documento fictício, em ordem")
                .extracting(ItemDaAnalise::numeroItem)
                .containsExactly(1, 2, 3);
        assertThat(lidos).allSatisfy(item ->
                assertThat(item.hashDoItem().valor()).matches("[0-9a-f]{64}"));
    }

    @Test
    void deveGuardarOsArquivosIlegiveisNaOrdemEmQueFalharam() {
        UUID execucaoId = auditoria.auditar(lote).execucao().id();

        registro.registrar(execucaoId, List.of(), List.of(
                new ArquivoIlegivel("primeiro.xml", "DocumentoFiscalIlegivel", "motivo ficticio 1"),
                new ArquivoIlegivel("segundo.xml", "DocumentoFiscalIlegivel", "motivo ficticio 2")));

        assertThat(consulta.arquivosIlegiveis(execucaoId))
                .extracting(ArquivoIlegivel::origem)
                .containsExactly("primeiro.xml", "segundo.xml");
    }

    @Test
    void deveDevolverListaVaziaQuandoNadaFalhou() {
        UUID execucaoId = auditoria.auditar(lote).execucao().id();
        registro.registrar(execucaoId, List.of(), List.of());

        assertThat(consulta.arquivosIlegiveis(execucaoId))
                .describedAs("vazio é resposta; nulo obrigaria quem lê a adivinhar")
                .isEmpty();
    }

    /**
     * A barreira de última instância: mesmo por SQL direto, a coluna recusa a
     * chave em texto claro.
     */
    @Test
    void oBancoDeveRecusarChaveDeAcessoEmTextoClaroNaOrigem() {
        UUID execucaoId = auditoria.auditar(lote).execucao().id();
        String nomeComChave = "9".repeat(44) + "-nfe.xml";

        assertThatThrownBy(() -> jdbc.update(
                "insert into falha_de_leitura_da_execucao "
                        + "(id, execucao_id, ordem, origem, tipo_de_erro, motivo) "
                        + "values (?, ?, 0, ?, ?, ?)",
                UUID.randomUUID(), execucaoId, nomeComChave, "Tipo", "motivo ficticio"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveGuardarOResumoQueAAnaliseLeuParaDenunciarReprocessamentoPosterior() {
        ResultadoDaAuditoria resultado = auditoria.auditar(lote);
        UUID execucaoId = resultado.execucao().id();
        registro.registrar(execucaoId, itensDe(resultado), List.of());

        ItemDaAnalise lido = consulta.itensDaExecucao(execucaoId).get(0);
        String hashGravadoNoItem = jdbc.queryForObject(
                "select hash_item from item_documento where chave_acesso = ? and numero_item = ?",
                String.class, lido.chaveAcesso().valor(), lido.numeroItem());

        assertThat(lido.hashDoItem().valor())
                .describedAs("enquanto ninguém reprocessou, os dois resumos coincidem — e é a "
                        + "divergência entre eles que denuncia o reprocessamento")
                .isEqualTo(hashGravadoNoItem);
    }

    /**
     * {@code recomecar-do-zero} apaga por "delete from documento" e
     * "delete from execucao_auditoria", nesta ordem, e não foi alterado nesta
     * etapa. As tabelas novas precisam sair junto sozinhas.
     */
    @Test
    void oRecomecoDoAcervoDeveLimparAsDuasTabelasNovasSemAlterarOComando() {
        ResultadoDaAuditoria resultado = auditoria.auditar(lote);
        UUID execucaoId = resultado.execucao().id();
        registro.registrar(execucaoId, itensDe(resultado), List.of(
                new ArquivoIlegivel("ilegivel.xml", "Tipo", "motivo ficticio")));

        assertThat(contar("item_da_execucao")).isPositive();
        assertThat(contar("falha_de_leitura_da_execucao")).isPositive();

        jdbc.update("delete from documento");
        jdbc.update("delete from execucao_auditoria");

        assertThat(contar("item_da_execucao")).isZero();
        assertThat(contar("falha_de_leitura_da_execucao")).isZero();
    }

    @Test
    void deveRecusarRegistroSemExecucao() {
        assertThatThrownBy(() -> registro.registrar(null, List.of(), List.of()))
                .isInstanceOf(RuntimeException.class);
    }

    private long contar(String tabela) {
        Long total = jdbc.queryForObject("select count(*) from " + tabela, Long.class);
        return total == null ? 0 : total;
    }

    private static List<ItemDaAnalise> itensDe(ResultadoDaAuditoria resultado) {
        List<ItemDaAnalise> itens = new ArrayList<>();
        for (DocumentoComItens documento : resultado.documentos()) {
            for (ItemDocumento item : documento.itensOrdenados()) {
                itens.add(new ItemDaAnalise(
                        documento.documento().chaveAcesso(),
                        item.numeroItem(),
                        HashDoItem.de(documento.documento().chaveAcesso(), item),
                        DescricaoDoProduto.de(Optional.of(DESCRICAO_FICTICIA))));
            }
        }
        return itens;
    }

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
