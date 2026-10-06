package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.catalogo.AcervoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// D026 (04/10/2026), decisão D-b: importar-catalogo com a pasta incompleta herda da carga indicada em --partir-de, que precisa ser a mais recente. Sem --partir-de, ou com outra carga, é recusado dizendo qual é a mais recente, com os dois instantes dela; nada é herdado em silêncio. No acervo vazio, a recusa é a de sempre. E a importação completa pela linha de comando também espera a trava do acervo. Valores fictícios.
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class ImportacaoParcialPelaCliTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

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
    private LinhaDeComando linhaDeComando;

    @Autowired
    private SaidaEmLista saida;

    @Autowired
    private AcervoDeCargas acervo;

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
        jdbc.execute("truncate table carga_catalogo cascade");
        saida.limpar();
    }

    @Test
    void pastaIncompletaComAcervoVazioDeveDarAMensagemDeHoje() throws IOException {
        Path incompleta = escrever("so-aliquotas", Map.of("aliquota-vigente.csv", aliquotas()));

        linhaDeComando.run("importar-catalogo", "--diretorio=" + incompleta, "--versao=carga-b");

        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
        assertThat(saida.texto()).contains("Falta o arquivo \"classificacao-tributaria.csv\"");
        assertThat(contarCargas()).isZero();
    }

    @Test
    void pastaIncompletaSemPartirDeDeveSerRecusadaNomeandoAMaisRecente() throws IOException {
        importarCompleta("carga-a");
        EstadoDaCarga maisRecente = acervo.estado("carga-a").orElseThrow();
        saida.limpar();
        Path incompleta = escrever("so-aliquotas", Map.of("aliquota-vigente.csv", aliquotas()));

        linhaDeComando.run("importar-catalogo", "--diretorio=" + incompleta, "--versao=carga-b");

        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
        assertThat(saida.texto()).contains("--partir-de=carga-a", "\"carga-a\"",
                "importada em " + maisRecente.importadoEm(), "nunca alterada", "Nada foi gravado",
                "classificacao-tributaria.csv");
        assertThat(contarCargas()).isEqualTo(1);
    }

    @Test
    void partirDeQueNaoEhAMaisRecenteDeveSerRecusado() throws IOException {
        importarCompleta("carga-a");
        importarCompleta("carga-c");
        saida.limpar();
        Path incompleta = escrever("so-aliquotas", Map.of("aliquota-vigente.csv", aliquotas()));

        linhaDeComando.run("importar-catalogo", "--diretorio=" + incompleta, "--versao=carga-b",
                "--partir-de=carga-a");

        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
        assertThat(saida.texto()).contains("--partir-de=carga-a não é a carga mais recente",
                "a mais recente é \"carga-c\"", "--partir-de=carga-c");
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void pastaIncompletaComPartirDeCorretoDeveCriarACargaDerivada() throws IOException {
        importarCompleta("carga-a");
        EstadoDaCarga origem = acervo.estado("carga-a").orElseThrow();
        saida.limpar();
        Path incompleta = escrever("so-aliquotas", Map.of("aliquota-vigente.csv", aliquotas()));

        linhaDeComando.run("importar-catalogo", "--diretorio=" + incompleta, "--versao=carga-b",
                "--partir-de=carga-a");

        assertThat(linhaDeComando.getExitCode()).as(saida.texto()).isZero();
        assertThat(saida.texto()).contains("a partir de \"carga-a\" (importada em " + origem.importadoEm()
                        + "; nunca alterada)",
                "tabelas da pasta: ALIQUOTA",
                "tabelas herdadas: CLASSIFICACAO_TRIBUTARIA, NCM, ITEM_ANEXO, COBERTURA, ANEXO_DECLARADO");
        assertThat(jdbc.queryForObject("""
                select origem.versao from carga_catalogo c join carga_catalogo origem on origem.id = c.derivada_de
                 where c.versao = 'carga-b'""", String.class)).isEqualTo("carga-a");
    }

    @Test
    void pastaCompletaComPartirDeDeveImportarSemHerancaEDizerQueAOrigemNaoFoiUsada() throws IOException {
        importarCompleta("carga-a");
        saida.limpar();

        linhaDeComando.run("importar-catalogo", "--diretorio=" + escrever("completa-b", catalogo()),
                "--versao=carga-b", "--partir-de=carga-a");

        assertThat(linhaDeComando.getExitCode()).as(saida.texto()).isZero();
        assertThat(saida.texto()).contains("nenhuma tabela herdada", "a origem informada, \"carga-a\", não foi usada");
        assertThat(jdbc.queryForObject("select derivada_de is null from carga_catalogo where versao = 'carga-b'",
                Boolean.class)).isTrue();
    }

    @Test
    void aImportacaoCompletaPelaCliDeveEsperarATravaDoAcervo() throws Exception {
        Path completa = escrever("completa-a", catalogo());
        CountDownLatch travada = new CountDownLatch(1);
        CountDownLatch liberacao = new CountDownLatch(1);
        CompletableFuture<Object> dona = CompletableFuture.supplyAsync(() -> acervo.comAcervoTravado(maisRecente -> {
            travada.countDown();
            try {
                liberacao.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException interrompida) {
                Thread.currentThread().interrupt();
            }
            return "solta";
        }));
        assertThat(travada.await(20, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> pelaCli = CompletableFuture.runAsync(() ->
                linhaDeComando.run("importar-catalogo", "--diretorio=" + completa, "--versao=carga-a"));
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted",
                Integer.class) < 1) {
            assertThat(pelaCli.isDone()).as("a CLI terminou sem esperar a trava do acervo").isFalse();
            assertThat(System.nanoTime()).as("a CLI não entrou na fila da trava").isLessThan(limite);
            Thread.sleep(20);
        }
        assertThat(existe("carga-a")).isFalse();
        liberacao.countDown();
        dona.get(20, TimeUnit.SECONDS);
        pelaCli.get(20, TimeUnit.SECONDS);

        assertThat(existe("carga-a")).as(saida.texto()).isTrue();
    }

    private void importarCompleta(String versao) throws IOException {
        linhaDeComando.run("importar-catalogo", "--diretorio=" + escrever("completa-" + versao, catalogo()),
                "--versao=" + versao);
        assertThat(linhaDeComando.getExitCode()).as(saida.texto()).isZero();
    }

    private Path escrever(String nome, Map<String, String> arquivos) throws IOException {
        Path diretorio = Files.createDirectories(pasta.resolve(nome));
        for (Map.Entry<String, String> arquivo : arquivos.entrySet()) {
            Files.writeString(diretorio.resolve(arquivo.getKey()), arquivo.getValue(), StandardCharsets.UTF_8);
        }
        return diretorio;
    }

    private boolean existe(String versao) {
        return jdbc.queryForObject("select count(*) from carga_catalogo where versao = ?", Integer.class, versao) > 0;
    }

    private int contarCargas() {
        return jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class);
    }

    private static String aliquotas() {
        return "tributo;percentual;abrangencia;" + DADOS + "\nCBS;99,99;ABRANGENCIA-XX;" + VIGENCIA + ";FICTICIO\n";
    }

    private static Map<String, String> catalogo() {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;Dispositivo ficticio;false;;;%s;FICTICIO;NENHUM
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("aliquota-vigente.csv", "tributo;percentual;abrangencia;" + DADOS + "\n");
        return arquivos;
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
