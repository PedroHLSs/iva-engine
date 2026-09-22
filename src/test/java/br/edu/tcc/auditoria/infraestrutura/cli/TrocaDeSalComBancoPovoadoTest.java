package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;
import br.edu.tcc.auditoria.infraestrutura.sal.ImpressaoDigitalDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.OrigemDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;
import br.edu.tcc.auditoria.infraestrutura.sal.SalResolvido;
import br.edu.tcc.auditoria.infraestrutura.sal.SalTrocado;
import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A recusa de subida contra um PostgreSQL de verdade, com acervo povoado.
 *
 * <h2>O que este teste prova que o teste de unidade não prova</h2>
 *
 * <p>Que a impressão digital atravessa a migration {@code V6}, as restrições da
 * tabela e a ida e volta pelo JPA — e que "o banco tem dados" é medido por
 * {@code documento}, e não por qualquer linha em qualquer tabela. A contagem sai
 * do banco, não de um dublê.</p>
 *
 * <h2>Sem Docker, o teste se desabilita</h2>
 *
 * <p>Em vez de falhar, como os demais testes de persistência deste projeto.</p>
 *
 * <h2>Os dados são fictícios</h2>
 *
 * <p>Chave de acesso com 44 zeros, pseudônimo com 64 zeros, modelo e série
 * inventados. Nada aqui se parece com documento real, e nenhum valor afirma
 * coisa alguma sobre a legislação.</p>
 */
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class TrocaDeSalComBancoPovoadoTest {

    private static final String SAL_DO_ACERVO = "sal-ficticio-que-produziu-o-acervo-aaaaa";
    private static final String SAL_TROCADO = "sal-ficticio-diferente-do-acervo-bbbbbbb";

    private static final String CHAVE_FICTICIA = "0".repeat(44);
    private static final String PSEUDONIMO_FICTICIO = "0".repeat(64);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> banco = new PostgreSQLContainer<>("postgres:16-alpine");

    static boolean dockerDisponivel() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private RegistroDaImpressaoDigital registro;

    @Autowired
    private AcervoSalgado acervo;

    @Autowired
    private JdbcTemplate jdbc;

    private final SaidaDeTeste saida = new SaidaDeTeste();

    @BeforeEach
    void limpar() {
        jdbc.update("delete from documento");
        jdbc.update("delete from execucao_auditoria");
        jdbc.update("delete from tratativa");
        jdbc.update("delete from impressao_digital_do_sal");
    }

    @Test
    void deveRecusarASubidaQuandoAImpressaoDigitalDivergirSobreBancoPovoado() {
        guarda(SAL_DO_ACERVO).run("auditar");
        gravarUmDocumento();

        assertThatThrownBy(() -> guarda(SAL_TROCADO).run("auditar"))
                .isInstanceOf(SalTrocado.class)
                .hasMessageContaining("1 documento");

        assertThat(saida.texto())
                .contains("RECUSADO")
                .doesNotContain(SAL_DO_ACERVO)
                .doesNotContain(SAL_TROCADO);
    }

    @Test
    void deveGravarAImpressaoDigitalAtravessandoAMigrationEAsRestricoes() {
        guarda(SAL_DO_ACERVO).run("auditar");

        Optional<RegistroDaImpressaoDigital.Registro> gravada = registro.registrada();

        assertThat(gravada).isPresent();
        assertThat(gravada.orElseThrow().impressao())
                .isEqualTo(ImpressaoDigitalDoSal.de(new SalDeInstalacao(SAL_DO_ACERVO)));
        assertThat(jdbc.queryForObject("select count(*) from impressao_digital_do_sal", Long.class))
                .as("a tabela guarda uma linha só")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select valor from impressao_digital_do_sal", String.class))
                .as("o que vai para o banco é o resumo, nunca o sal")
                .isNotEqualTo(SAL_DO_ACERVO)
                .matches("[0-9a-f]{64}");
    }

    /**
     * Catálogo importado não conta como "o banco tem dados": ele não tem sal
     * nenhum, e recusar por causa dele seria travar por um motivo que não existe.
     */
    @Test
    void naoDeveRecusarQuandoOBancoSoTiverCatalogoImportado() {
        guarda(SAL_DO_ACERVO).run("auditar");

        assertThat(acervo.quantidadeDeDocumentos()).isZero();
        assertThatCode(() -> guarda(SAL_TROCADO).run("auditar")).doesNotThrowAnyException();
    }

    @Test
    void oRecomecoDeveApagarOsDocumentosEPreservarAsTratativas() {
        gravarUmDocumento();
        gravarUmaTratativa();

        AcervoSalgado.Apagamento apagamento = acervo.apagar();

        assertThat(apagamento.documentos()).isEqualTo(1);
        assertThat(apagamento.tratativasPreservadas()).isEqualTo(1);
        assertThat(acervo.quantidadeDeDocumentos()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tratativa", Long.class))
                .as("a chave da tratativa é o hash do item, que não leva sal: ela não é atingida")
                .isEqualTo(1L);
    }

    private void gravarUmDocumento() {
        jdbc.update("""
                insert into documento (chave_acesso, modelo, serie, numero, data_emissao,
                                       uf_emitente, emitente_pseudonimizado, registrado_em)
                values (?, '99', '9', '999', date '2026-01-01', 'ZZ', ?, now())
                """, CHAVE_FICTICIA, PSEUDONIMO_FICTICIO);
    }

    private void gravarUmaTratativa() {
        jdbc.update("""
                insert into tratativa (id, hash_item, regra_id, regra_versao, decisao,
                                       justificativa, registrado_em)
                values (gen_random_uuid(), ?, 'R99', '9.9.9', 'ACEITO', 'justificativa fictícia', now())
                """, PSEUDONIMO_FICTICIO);
    }

    private GuardaDeSalNaSubida guarda(String sal) {
        SalResolvido resolvido = new SalResolvido(
                new SalDeInstalacao(sal), OrigemDoSal.ARQUIVO_LOCAL, Optional.empty(), Optional.empty());
        return new GuardaDeSalNaSubida(resolvido, registro, acervo, saida);
    }

    private static final class SaidaDeTeste implements Saida {

        private final List<String> linhas = new ArrayList<>();

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        String texto() {
            return String.join("\n", linhas);
        }
    }
}
