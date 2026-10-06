package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.IncidenciaDaReducao;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D017 (03/10/2026): a incidência da redução atravessa a gravação (V19) e volta igual, e o banco recusa valor fora de ALIQUOTA e BASE. Valores fictícios: códigos XXX0nn, CST AAA, NCM 00000000, vigência desde 1900.
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class ReducaoIncideSobreNoBancoTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String VERSAO = "carga-ficticia-d017";
    private static final LocalDate DATA = LocalDate.of(1900, 6, 1);

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
    void osTresEstadosDevemVoltarIguais() {
        assertThat(lida("XXX001").reducaoIncideSobre()).isEmpty();
        assertThat(lida("XXX002").reducaoIncideSobre()).contains(IncidenciaDaReducao.ALIQUOTA);
        assertThat(lida("XXX003").reducaoIncideSobre()).contains(IncidenciaDaReducao.BASE);
    }

    @Test
    void oBancoDeveRecusarValorForaDaForma() {
        assertThatThrownBy(() -> jdbc.update(
                "update classificacao_tributaria set reducao_incide_sobre = 'base' where codigo = 'XXX001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ClassificacaoTributaria lida(String codigo) {
        return catalogos.daVersao(VERSAO).orElseThrow().classificacoesTributarias()
                .buscarVigenteEm(new CodigoClassificacaoTributaria(codigo), DATA).orElseThrow();
    }

    private static CargaDeCatalogo carga() {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
        List<ClassificacaoTributaria> classificacoes = List.of(
                classificacao("XXX001", Optional.empty(), procedencia),
                classificacao("XXX002", Optional.of(IncidenciaDaReducao.ALIQUOTA), procedencia),
                classificacao("XXX003", Optional.of(IncidenciaDaReducao.BASE), procedencia));
        List<RegistroNcm> ncms = List.of(new RegistroNcm(new Ncm("00000000"), "DESCRICAO FICTICIA", procedencia));
        List<ItemAnexo> anexos = List.of(new ItemAnexo(
                new Ncm("00000000"), new IdentificadorAnexo("ANEXO-XX"), "TRATAMENTO-FICTICIO", procedencia));
        return new CargaDeCatalogo(VERSAO, new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO, classificacoes, ncms, anexos, List.of()),
                classificacoes, ncms, anexos, List.of());
    }

    private static ClassificacaoTributaria classificacao(
            String codigo, Optional<IncidenciaDaReducao> incidencia, ProcedenciaNormativa procedencia) {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(codigo), Set.of(new CodigoCst("AAA")),
                "DISPOSITIVO FICTICIO PARA TESTE", true, Optional.of(new BigDecimal("99.99")), incidencia,
                Optional.empty(), Optional.empty(), Optional.of(List.of()), procedencia);
    }
}
