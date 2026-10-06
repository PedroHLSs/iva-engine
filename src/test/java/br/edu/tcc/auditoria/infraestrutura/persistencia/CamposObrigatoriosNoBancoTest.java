package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D015 (03/10/2026): os três estados dos campos exigidos atravessam a gravação e voltam iguais, contra um PostgreSQL de verdade com a V17 aplicada. Inclui a carga anterior à V17, com a coluna nula. Valores fictícios: códigos XXX0nn, CST AAA, NCM 00000000, vigência desde 1900.
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class CamposObrigatoriosNoBancoTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String VERSAO = "carga-ficticia-d015";
    private static final LocalDate DATA = LocalDate.of(1900, 6, 1);
    private static final String NAO_DECLARADO = "XXX001";
    private static final String NENHUM = "XXX002";
    private static final String COM_LISTA = "XXX003";

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacao;

    @Autowired
    private ProvedorDeCatalogoPorVersao catalogos;

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
    void gravarCarga() {
        jdbc.execute("truncate table carga_catalogo cascade");
        importacao.importar(carga());
    }

    @Test
    void naoDeclaradoDeveVoltarNaoDeclarado() {
        assertThat(lida(NAO_DECLARADO).camposObrigatoriosCondicionados()).isEmpty();
        assertThat(coluna(NAO_DECLARADO)).isFalse();
    }

    @Test
    void nenhumDeveVoltarComoListaVaziaDeclarada() {
        assertThat(lida(NENHUM).camposObrigatoriosCondicionados()).isEqualTo(Optional.of(List.of()));
        assertThat(coluna(NENHUM)).isTrue();
    }

    @Test
    void listaDeveVoltarNaOrdemGravada() {
        assertThat(lida(COM_LISTA).camposObrigatoriosCondicionados())
                .isEqualTo(Optional.of(List.of("valorCbs", "baseCalculoIbs")));
        assertThat(coluna(COM_LISTA)).isTrue();
    }

    // Carga anterior à V17: a coluna é nula. Lista vazia ali era branco ou NENHUM, sem como saber, e fica "não declarado"; lista com nomes continua declaração.
    @Test
    void cargaAnteriorAV17DeveLerListaVaziaComoNaoDeclarada() {
        jdbc.update("update classificacao_tributaria set campos_obrigatorios_declarados = null");

        assertThat(lida(NENHUM).camposObrigatoriosCondicionados()).isEmpty();
        assertThat(lida(COM_LISTA).camposObrigatoriosCondicionados())
                .isEqualTo(Optional.of(List.of("valorCbs", "baseCalculoIbs")));
    }

    // Método auxiliar que relê a classificação pelo provedor que reabre análises, o mesmo caminho da tela.
    private ClassificacaoTributaria lida(String codigo) {
        CatalogoParaAuditoria catalogo = catalogos.daVersao(VERSAO).orElseThrow();
        return catalogo.classificacoesTributarias()
                .buscarVigenteEm(new CodigoClassificacaoTributaria(codigo), DATA)
                .orElseThrow();
    }

    // Método auxiliar que lê a coluna da V17 direto no banco.
    private Boolean coluna(String codigo) {
        return jdbc.queryForObject(
                "select campos_obrigatorios_declarados from classificacao_tributaria where codigo = ?",
                Boolean.class, codigo);
    }

    // Método auxiliar que monta a carga fictícia com os três estados.
    private static CargaDeCatalogo carga() {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(
                classificacao(NAO_DECLARADO, Optional.empty(), procedencia),
                classificacao(NENHUM, Optional.of(List.of()), procedencia),
                classificacao(COM_LISTA, Optional.of(List.of("valorCbs", "baseCalculoIbs")), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        List<ItemAnexo> anexos = List.of(new ItemAnexo(
                new Ncm("00000000"), new IdentificadorAnexo("ANEXO-XX"), "TRATAMENTO-FICTICIO", procedencia));
        return new CargaDeCatalogo(VERSAO, new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO, classificacoes, ncms, anexos, List.of()),
                classificacoes, ncms, anexos, List.of());
    }

    // Método auxiliar que monta uma classificação fictícia pelo construtor canônico.
    private static ClassificacaoTributaria classificacao(
            String codigo, Optional<List<String>> campos, ProcedenciaNormativa procedencia) {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(codigo),
                Set.of(new CodigoCst("AAA")),
                "DISPOSITIVO FICTICIO PARA TESTE",
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                campos,
                procedencia);
    }
}
