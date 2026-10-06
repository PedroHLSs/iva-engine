package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.AcervoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.EfeitoDaEdicao;
import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.OrigemDaImportacaoMudou;
import br.edu.tcc.auditoria.aplicacao.catalogo.ResultadoDaImportacao;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas.OrigemVista;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.SubstituicaoDeTabelas;
import br.edu.tcc.auditoria.infraestrutura.catalogo.CargaRecusada;
import br.edu.tcc.auditoria.infraestrutura.catalogo.FontesDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D026 (04/10/2026): a trava do acervo contra PostgreSQL de verdade. Todo caminho que muda qual é a carga mais recente, ou o conteúdo de uma possível origem, espera a trava: importação completa e parcial, edição nos dois efeitos e exclusão. A selagem não espera. Com a trava segura por uma thread, cada caminho é posto numa fila conferida em pg_locks, e a ordem da fila decide o resultado, sem depender de sorte. E a gravação de cada caminho roda na mesma transação da trava: uma exceção lá dentro desfaz tudo. Valores fictícios.
@SpringBootTest(properties = {
        "auditoria.tolerancia-de-valor=0.01",
        "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
})
@Testcontainers
@EnabledIf("dockerDisponivel")
class ImportacaoParcialConcorrenteTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";
    private static final long ESPERA_MAXIMA_EM_SEGUNDOS = 20;

    @Autowired
    private ServicoDeCargas cargas;

    @Autowired
    private AcervoDeCargas acervo;

    @Autowired
    private ServicoDeImportacaoDeCatalogo importacao;

    @Autowired
    private ProvedorDeCatalogo provedor;

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
        jdbc.execute("truncate table carga_catalogo cascade");
    }

    @Test
    void duasParciaisDaMesmaOrigemDevemPassarUmaSoEAOutraReceber409() throws Exception {
        importarCompleta("carga-a", "Dispositivo ficticio A");
        Optional<OrigemVista> vista = vista("carga-a");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> primeira = trava.enfileirar(() -> parcial("carga-b", vista));
            CompletableFuture<Object> segunda = trava.enfileirar(() -> parcial("carga-c", vista));
            trava.soltar();

            assertThat(resultado(primeira)).isInstanceOf(ResultadoDaImportacao.class);
            assertThat(resultado(segunda)).isInstanceOfSatisfying(OrigemDaImportacaoMudou.class,
                    recusa -> assertThat(recusa.origemAtual().versao()).isEqualTo("carga-b"));
        }
        assertThat(existe("carga-b")).isTrue();
        assertThat(existe("carga-c")).isFalse();
    }

    @Test
    void parcialContraEditarQueCriaVersaoDeveReceber409ComAVersaoNova() throws Exception {
        importarCompleta("carga-a", "Dispositivo ficticio A");
        selar("carga-a");
        Optional<OrigemVista> vista = vista("carga-a");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> edicao = trava.enfileirar(() -> cargas.editar("carga-a",
                    trocaDeClassificacao("Dispositivo ficticio editado"), EfeitoDaEdicao.CRIAR_VERSAO_NOVA,
                    Optional.of("carga-a-ed1")));
            CompletableFuture<Object> parcial = trava.enfileirar(() -> parcial("carga-b", vista));
            trava.soltar();

            assertThat(resultado(edicao)).isNotInstanceOf(Throwable.class);
            assertThat(resultado(parcial)).isInstanceOfSatisfying(OrigemDaImportacaoMudou.class,
                    recusa -> assertThat(recusa.origemAtual().versao()).isEqualTo("carga-a-ed1"));
        }
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void parcialContraEditarNoLugarDoRascunhoDeveReceber409() throws Exception {
        importarCompleta("carga-a", "Dispositivo ficticio A");
        Optional<OrigemVista> vista = vista("carga-a");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> edicao = trava.enfileirar(() -> cargas.editar("carga-a",
                    trocaDeClassificacao("Dispositivo ficticio alterado"), EfeitoDaEdicao.ALTERAR_RASCUNHO,
                    Optional.empty()));
            CompletableFuture<Object> parcial = trava.enfileirar(() -> parcial("carga-b", vista));
            trava.soltar();

            assertThat(resultado(edicao)).isNotInstanceOf(Throwable.class);
            assertThat(resultado(parcial)).isInstanceOf(OrigemDaImportacaoMudou.class)
                    .extracting(Object::toString).asString().contains("foi alterada depois que a tela a mostrou");
        }
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void parcialContraExclusaoDaMaisRecenteDeveReceber409ComAAnterior() throws Exception {
        importarCompleta("carga-w", "Dispositivo ficticio W");
        importarCompleta("carga-x", "Dispositivo ficticio X");
        Optional<OrigemVista> vista = vista("carga-x");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> exclusao = trava.enfileirar(() -> {
                cargas.excluir("carga-x");
                return "excluida";
            });
            CompletableFuture<Object> parcial = trava.enfileirar(() -> parcial("carga-b", vista));
            trava.soltar();

            assertThat(resultado(exclusao)).isEqualTo("excluida");
            assertThat(resultado(parcial)).isInstanceOfSatisfying(OrigemDaImportacaoMudou.class,
                    recusa -> assertThat(recusa.origemAtual().versao()).isEqualTo("carga-w"));
        }
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void parcialContraRascunhoExcluidoERecriadoComOMesmoNomeDeveReceber409() throws Exception {
        importarCompleta("carga-w", "Dispositivo ficticio W");
        importarCompleta("carga-x", "Dispositivo ficticio X");
        Optional<OrigemVista> vista = vista("carga-x");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> exclusao = trava.enfileirar(() -> {
                cargas.excluir("carga-x");
                return "excluida";
            });
            CompletableFuture<Object> recriacao = trava.enfileirar(() -> cargas.importarCompleta(
                    ler("carga-x", catalogo("Dispositivo ficticio X de novo")), Optional.empty()));
            CompletableFuture<Object> parcial = trava.enfileirar(() -> parcial("carga-b", vista));
            trava.soltar();

            assertThat(resultado(exclusao)).isEqualTo("excluida");
            assertThat(resultado(recriacao)).isNotInstanceOf(Throwable.class);
            assertThat(resultado(parcial)).isInstanceOfSatisfying(OrigemDaImportacaoMudou.class, recusa -> {
                assertThat(recusa.origemAtual().versao()).isEqualTo("carga-x");
                assertThat(recusa.getMessage()).contains("excluída e importada de novo");
            });
        }
        assertThat(existe("carga-b")).isFalse();
    }

    @Test
    void acervoEsvaziadoPorExclusaoDeveVoltarAExigirOsCinco() {
        importarCompleta("carga-x", "Dispositivo ficticio X");
        Optional<OrigemVista> vista = vista("carga-x");
        cargas.excluir("carga-x");

        assertThatThrownBy(() -> parcial("carga-b", vista))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("Falta o arquivo \"classificacao-tributaria.csv\"");
        assertThat(contarCargas()).isZero();
    }

    @Test
    void parcialEnfileiradaAtrasDaExclusaoDaUnicaCargaDeveExigirOsCinco() throws Exception {
        importarCompleta("carga-x", "Dispositivo ficticio X");
        Optional<OrigemVista> vista = vista("carga-x");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> exclusao = trava.enfileirar(() -> {
                cargas.excluir("carga-x");
                return "excluida";
            });
            // Fora da trava a parcial ainda vê "carga-x"; a decisão que vale é a de dentro dela.
            CompletableFuture<Object> parcial = trava.enfileirar(() -> parcial("carga-b", vista));
            trava.soltar();

            assertThat(resultado(exclusao)).isEqualTo("excluida");
            assertThat(resultado(parcial)).isInstanceOf(CargaRecusada.class)
                    .extracting(Object::toString).asString().contains("Falta o arquivo");
        }
        assertThat(contarCargas()).isZero();
    }

    @Test
    void cadaCaminhoQueMudaACargaMaisRecenteDeveEsperarATravaDoAcervo() throws Exception {
        importarCompleta("carga-a", "Dispositivo ficticio A");

        Map<String, Supplier<Object>> caminhos = new LinkedHashMap<>();
        caminhos.put("importação completa", () -> cargas.importarCompleta(
                ler("carga-c", catalogo("Dispositivo ficticio C")), Optional.empty()));
        caminhos.put("importação parcial", () -> parcial("carga-b", vista("carga-c")));
        caminhos.put("edição no lugar", () -> cargas.editar("carga-b",
                trocaDeClassificacao("Dispositivo ficticio B2"), EfeitoDaEdicao.ALTERAR_RASCUNHO, Optional.empty()));
        caminhos.put("edição que cria versão", () -> {
            selar("carga-b");
            return cargas.editar("carga-b", trocaDeClassificacao("Dispositivo ficticio B3"),
                    EfeitoDaEdicao.CRIAR_VERSAO_NOVA, Optional.of("carga-b-ed1"));
        });
        caminhos.put("exclusão", () -> {
            cargas.excluir("carga-b-ed1");
            return "excluida";
        });

        for (Map.Entry<String, Supplier<Object>> caminho : caminhos.entrySet()) {
            try (TravaSegura trava = segurarATrava()) {
                CompletableFuture<Object> esperando = trava.enfileirar(caminho.getValue());
                assertThat(esperando.isDone()).as(caminho.getKey() + " não esperou a trava do acervo").isFalse();
                trava.soltar();
                assertThat(resultado(esperando)).as(caminho.getKey()).isNotInstanceOf(Throwable.class);
            }
        }
    }

    @Test
    void aSelagemNaoDeveEsperarATravaDoAcervo() throws Exception {
        importarCompleta("carga-a", "Dispositivo ficticio A");

        try (TravaSegura trava = segurarATrava()) {
            CompletableFuture<Object> selagem = CompletableFuture.supplyAsync(provedor::carregar);
            assertThat(selagem.get(ESPERA_MAXIMA_EM_SEGUNDOS, TimeUnit.SECONDS)).isNotNull();
            trava.soltar();
        }
        assertThat(jdbc.queryForObject("select selada_em is not null from carga_catalogo where versao = 'carga-a'",
                Boolean.class)).isTrue();
    }

    @Test
    void aGravacaoDaCargaCompletaDeveRodarNaTransacaoDaTrava() {
        CargaDeCatalogo carga = ler("carga-a", catalogo("Dispositivo ficticio A"));

        assertThatThrownBy(() -> acervo.comAcervoTravado(maisRecente -> {
            importacao.importar(carga);
            assertThat(travaDoAcervoNestaConexao()).as("a trava do acervo na conexão da gravação").isTrue();
            assertThat(existe("carga-a")).as("a gravação é vista dentro da transação").isTrue();
            throw new DesfazerParaProvar();
        })).isInstanceOf(DesfazerParaProvar.class);

        assertThat(existe("carga-a")).as("a gravação foi desfeita junto com a transação da trava").isFalse();
    }

    @Test
    void aGravacaoDaDerivadaDaSubstituicaoEDaExclusaoDeveRodarNaTransacaoDaTrava() {
        importarCompleta("carga-a", "Dispositivo ficticio A");
        String antes = impressaoDigital("carga-a");
        CargaDeCatalogo outra = ler("carga-a", catalogo("Dispositivo ficticio trocado"));
        CargaDeCatalogo derivada = ler("carga-b", catalogo("Dispositivo ficticio B"));

        Map<String, Runnable> gravacoes = new LinkedHashMap<>();
        gravacoes.put("salvarDerivada", () -> acervo.salvarDerivada(derivada, "carga-a"));
        gravacoes.put("substituirConteudo", () -> acervo.substituirConteudo("carga-a", outra));
        gravacoes.put("excluir", () -> acervo.excluir("carga-a"));

        for (Map.Entry<String, Runnable> gravacao : gravacoes.entrySet()) {
            assertThatThrownBy(() -> acervo.comAcervoTravado(maisRecente -> acervo.comCargaTravada("carga-a",
                    estado -> {
                        gravacao.getValue().run();
                        assertThat(travaDoAcervoNestaConexao()).as(gravacao.getKey()).isTrue();
                        throw new DesfazerParaProvar();
                    }))).as(gravacao.getKey()).isInstanceOf(DesfazerParaProvar.class);
            assertThat(impressaoDigital("carga-a")).as(gravacao.getKey()).isEqualTo(antes);
            assertThat(existe("carga-b")).as(gravacao.getKey()).isFalse();
        }
    }

    // Exceção lançada dentro da trava só para provar que a gravação é desfeita com ela.
    private static final class DesfazerParaProvar extends RuntimeException {
        DesfazerParaProvar() {
            super("desfazer para provar que a gravação está na transação da trava");
        }
    }

    // Segura a trava do acervo numa thread, até soltar; enfileira caminhos e confere em pg_locks que esperam.
    private TravaSegura segurarATrava() throws InterruptedException {
        TravaSegura trava = new TravaSegura();
        trava.dona = CompletableFuture.supplyAsync(() -> acervo.comAcervoTravado(maisRecente -> {
            trava.travada.countDown();
            try {
                trava.liberacao.await(ESPERA_MAXIMA_EM_SEGUNDOS * 3, TimeUnit.SECONDS);
            } catch (InterruptedException interrompida) {
                Thread.currentThread().interrupt();
            }
            return "solta";
        }));
        assertThat(trava.travada.await(ESPERA_MAXIMA_EM_SEGUNDOS, TimeUnit.SECONDS)).isTrue();
        return trava;
    }

    private final class TravaSegura implements AutoCloseable {

        private final CountDownLatch travada = new CountDownLatch(1);
        private final CountDownLatch liberacao = new CountDownLatch(1);
        private CompletableFuture<Object> dona;
        private int naFila;

        // Põe o caminho para rodar em outra thread e só volta quando ele está na fila da trava, em pg_locks.
        CompletableFuture<Object> enfileirar(Supplier<Object> caminho) throws InterruptedException {
            CompletableFuture<Object> futuro = CompletableFuture.supplyAsync(() -> {
                try {
                    return caminho.get();
                } catch (RuntimeException recusa) {
                    return recusa;
                }
            });
            naFila++;
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_MAXIMA_EM_SEGUNDOS);
            while (esperandoATrava() < naFila) {
                assertThat(futuro.isDone()).as("o caminho terminou sem esperar a trava: " + futuro.getNow(null))
                        .isFalse();
                assertThat(System.nanoTime()).as("o caminho não entrou na fila da trava").isLessThan(limite);
                Thread.sleep(20);
            }
            return futuro;
        }

        void soltar() {
            liberacao.countDown();
        }

        @Override
        public void close() throws Exception {
            liberacao.countDown();
            dona.get(ESPERA_MAXIMA_EM_SEGUNDOS, TimeUnit.SECONDS);
        }
    }

    private int esperandoATrava() {
        return jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted",
                Integer.class);
    }

    private boolean travaDoAcervoNestaConexao() {
        return jdbc.queryForObject("""
                select count(*) from pg_locks
                 where locktype = 'advisory' and granted and pid = pg_backend_pid()""", Integer.class) == 1;
    }

    private static Object resultado(CompletableFuture<Object> futuro)
            throws InterruptedException, ExecutionException, TimeoutException {
        return futuro.get(ESPERA_MAXIMA_EM_SEGUNDOS, TimeUnit.SECONDS);
    }

    private Optional<OrigemVista> vista(String versao) {
        EstadoDaCarga estado = acervo.estado(versao).orElseThrow();
        return Optional.of(OrigemVista.pelaTela(versao, Optional.of(estado.importadoEm()), estado.alteradaEm()));
    }

    private Object parcial(String versao, Optional<OrigemVista> vista) {
        Map<String, String> arquivos = Map.of("aliquota-vigente.csv", "tributo;percentual;abrangencia;" + DADOS
                + "\nCBS;99,99;ABRANGENCIA-XX;" + VIGENCIA + ";FICTICIO\n");
        return cargas.importarParcial(versao, () -> substituicao(arquivos), vista,
                versaoLida -> ler(versaoLida, arquivos));
    }

    private void importarCompleta(String versao, String dispositivo) {
        cargas.importarCompleta(ler(versao, catalogo(dispositivo)), Optional.empty());
    }

    private void selar(String versao) {
        jdbc.update("update carga_catalogo set selada_em = ? where versao = ?", Timestamp.from(Instant.now()), versao);
    }

    private boolean existe(String versao) {
        return jdbc.queryForObject("select count(*) from carga_catalogo where versao = ?", Integer.class, versao) > 0;
    }

    private int contarCargas() {
        return jdbc.queryForObject("select count(*) from carga_catalogo", Integer.class);
    }

    private String impressaoDigital(String versao) {
        StringBuilder impressao = new StringBuilder(jdbc.queryForObject(
                "select (to_jsonb(c) - 'id')::text from carga_catalogo c where c.versao = ?", String.class, versao));
        for (String tabela : List.of("classificacao_tributaria", "registro_ncm", "item_anexo", "aliquota_vigente",
                "cobertura_catalogo", "natureza_da_carga", "anexo_declarado")) {
            impressao.append('#').append(jdbc.queryForObject(("""
                    select coalesce(string_agg((to_jsonb(t) - 'id' - 'carga_id')::text, '|'
                           order by (to_jsonb(t) - 'id' - 'carga_id')::text), '')
                      from %s t join carga_catalogo c on c.id = t.carga_id where c.versao = ?""").formatted(tabela),
                    String.class, versao));
        }
        return impressao.toString();
    }

    private static SubstituicaoDeTabelas trocaDeClassificacao(String dispositivo) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("classificacao-tributaria.csv", classificacao(dispositivo));
        arquivos.put("cobertura.csv", catalogo(dispositivo).get("cobertura.csv"));
        return substituicao(arquivos);
    }

    private static Map<String, String> catalogo(String dispositivo) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", classificacao(dispositivo));
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

    private static String classificacao(String dispositivo) {
        return """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;%s;false;;;%s;FICTICIO;NENHUM
                """.formatted(DADOS, dispositivo, VIGENCIA);
    }

    private static CargaDeCatalogo ler(String versao, Map<String, String> arquivos) {
        try {
            return LeitorDeCatalogoEmCsv.ler(fontes(arquivos), versao);
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }

    private static SubstituicaoDeTabelas substituicao(Map<String, String> arquivos) {
        try {
            return LeitorDeCatalogoEmCsv.lerSubstituicao(fontes(arquivos));
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }

    private static FontesDoCatalogo fontes(Map<String, String> arquivos) {
        Map<String, byte[]> porNome = new LinkedHashMap<>();
        arquivos.forEach((nome, conteudo) -> porNome.put(nome, conteudo.getBytes(StandardCharsets.UTF_8)));
        return FontesDoCatalogo.emMemoria(porNome);
    }
}
