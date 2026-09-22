package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MontadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.infraestrutura.xml.Pseudonimizador;
import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda automatizada da regra de que a exportação não leva identificador em
 * texto claro.
 *
 * <p>A chave de acesso não é um identificador neutro: os dígitos dela contêm o
 * CNPJ do emitente. Exportá-la seria exportar o CNPJ com um passo a mais de
 * trabalho para lê-lo — e a planilha é justamente o artefato que sai da máquina,
 * vai por e-mail e para numa pasta compartilhada.</p>
 *
 * <p>A verificação é feita sobre o arquivo cru, e não sobre as células. Um xlsx é
 * um zip de XML: varrer o conteúdo descompactado pega o vazamento mesmo que ele
 * caia num lugar que a leitura por células não visitaria — nome de aba, cabeçalho
 * de impressão, propriedade do documento.</p>
 */
class NenhumIdentificadorEmTextoClaroNaExportacaoTest {

    private static final String CHAVE_DE_ACESSO = "12345678901234567890123456789012345678901234";
    private static final String CNPJ_NA_CHAVE = "34567890123456";
    private static final String SAL_FICTICIO = "sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa";

    /** Qualquer corrida de 44 dígitos: é a forma de uma chave de acesso. */
    private static final Pattern FORMA_DE_CHAVE = Pattern.compile("\\d{44}");

    @TempDir
    private Path pasta;

    private Path arquivo;

    @BeforeEach
    void exportarComChaveDeVerdade() {
        arquivo = pasta.resolve("papel-de-trabalho.xlsx");
        new ExportadorXlsx(ZoneOffset.UTC).exportar(papelComChaveDeVerdade(), arquivo);
    }

    @Test
    void naoDeveEscreverAChaveDeAcessoEmLugarNenhumDoArquivo() throws IOException {
        assertThat(conteudoDescompactado())
                .as("a chave de acesso não pode aparecer na planilha, em célula nenhuma "
                        + "e em nenhuma parte interna do arquivo")
                .doesNotContain(CHAVE_DE_ACESSO);
    }

    @Test
    void naoDeveEscreverOCnpjQueEstaDentroDaChave() throws IOException {
        assertThat(conteudoDescompactado())
                .as("exportar a chave exportaria o CNPJ do emitente junto")
                .doesNotContain(CNPJ_NA_CHAVE);
    }

    @Test
    void naoDeveConterNenhumaSequenciaComFormaDeChaveDeAcesso() throws IOException {
        assertThat(FORMA_DE_CHAVE.matcher(conteudoDescompactado()).find())
                .as("nenhuma corrida de 44 dígitos deve existir no arquivo, venha ela de onde vier")
                .isFalse();
    }

    @Test
    void deveEscreverOPseudonimoNoLugar() throws IOException {
        String pseudonimo = pseudonimizador().pseudonimizar(CHAVE_DE_ACESSO).valor();

        assertThat(conteudoDescompactado())
                .as("o documento continua identificável de forma estável, sem identificar ninguém")
                .contains(pseudonimo);
    }

    // -----------------------------------------------------------------------
    // Descrição do produto. Acrescentado na etapa de conferência.
    //
    // xProd passou a ser o SEGUNDO campo de texto livre do sistema, e o primeiro
    // que vem da fonte em escala e sem revisão: na prática traz nome de cliente,
    // referência de pedido, "P/ OBRA FULANO". Ele é gravado no acervo da análise
    // e pode ser exibido em tela por configuração — mas a planilha é o artefato
    // que sai da máquina, e ele não vai junto.
    //
    // A verificação aqui é de forma, e não de conteúdo, porque não há conteúdo a
    // plantar: a descrição não chega ao papel de trabalho. É isso que estes dois
    // testes fixam. No dia em que alguém a trouxer para cá, eles quebram, e a
    // quebra é a decisão sendo pedida em vez de tomada por descuido.
    // -----------------------------------------------------------------------

    @Test
    void asLinhasDoPapelDeTrabalhoNaoPodemCarregarDescricaoDeProduto() {
        List<Class<?>> tipos = List.of(
                br.edu.tcc.auditoria.aplicacao.papeldetrabalho.LinhaDeAchado.class,
                br.edu.tcc.auditoria.aplicacao.papeldetrabalho.LinhaNaoAvaliada.class,
                PapelDeTrabalho.class);

        List<String> acusacoes = new ArrayList<>();
        int conferidos = 0;
        for (Class<?> tipo : tipos) {
            java.lang.reflect.RecordComponent[] componentes = tipo.getRecordComponents();
            assertThat(componentes)
                    .as("autoverificação: %s precisa ser record com componentes", tipo.getSimpleName())
                    .isNotEmpty();
            for (java.lang.reflect.RecordComponent componente : componentes) {
                conferidos++;
                String nome = componente.getName().toLowerCase(java.util.Locale.ROOT);
                if (nome.contains("descricao") || nome.contains("xprod")) {
                    acusacoes.add(tipo.getSimpleName() + "." + componente.getName());
                }
            }
        }

        assertThat(conferidos)
                .as("autoverificação: a varredura precisa ter olhado alguma coisa")
                .isGreaterThan(10);
        assertThat(acusacoes)
                .as("a planilha sai da máquina; o texto livre do emitente não sai com ela")
                .isEmpty();
    }

    @Test
    void oCodigoDaExportacaoNaoPodeMencionarADescricaoDoProduto() throws IOException {
        List<Path> pacotes = List.of(
                Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria",
                        "aplicacao", "papeldetrabalho"),
                Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria",
                        "infraestrutura", "exportacao"));

        List<String> acusacoes = new ArrayList<>();
        int arquivosLidos = 0;
        for (Path pacote : pacotes) {
            try (var caminhos = java.nio.file.Files.walk(pacote)) {
                for (Path arquivoJava : caminhos.filter(java.nio.file.Files::isRegularFile).toList()) {
                    if (!arquivoJava.toString().endsWith(".java")) {
                        continue;
                    }
                    arquivosLidos++;
                    List<String> linhas =
                            java.nio.file.Files.readAllLines(arquivoJava, StandardCharsets.UTF_8);
                    for (int numero = 0; numero < linhas.size(); numero++) {
                        String linha = linhas.get(numero);
                        if (ehComentario(linha)) {
                            continue;
                        }
                        if (linha.contains("DescricaoDoProduto") || linha.contains("descricao_produto")) {
                            acusacoes.add("%s:%d — %s"
                                    .formatted(arquivoJava, numero + 1, linha.strip()));
                        }
                    }
                }
            }
        }

        assertThat(arquivosLidos)
                .as("autoverificação: a varredura precisa ter aberto arquivo")
                .isGreaterThan(5);
        assertThat(acusacoes)
                .as("citar o tipo em comentário continua permitido; usá-lo, não")
                .isEmpty();
    }

    /** Comentário e Javadoc podem citar o que o código não pode usar. */
    private static boolean ehComentario(String linha) {
        String limpa = linha.strip();
        return limpa.startsWith("//") || limpa.startsWith("*") || limpa.startsWith("/*");
    }

    /**
     * Concatena o conteúdo de todas as partes internas do xlsx.
     *
     * <p>Pelo diretório central do zip, e não por fluxo: o POI grava as entradas
     * sem declarar o tamanho no cabeçalho local, e a leitura em fluxo recusa o
     * conteúdo por divergência de tamanho.</p>
     */
    private String conteudoDescompactado() throws IOException {
        StringBuilder conteudo = new StringBuilder();
        try (ZipFile zip = new ZipFile(arquivo.toFile())) {
            Enumeration<? extends ZipEntry> entradas = zip.entries();
            while (entradas.hasMoreElements()) {
                ZipEntry entrada = entradas.nextElement();
                conteudo.append(entrada.getName()).append('\n');
                try (InputStream parte = zip.getInputStream(entrada)) {
                    conteudo.append(new String(parte.readAllBytes(), StandardCharsets.UTF_8))
                            .append('\n');
                }
            }
        }
        return conteudo.toString();
    }

    /**
     * Papel de trabalho montado a partir de uma chave de acesso de verdade, para
     * que o caminho testado seja o mesmo do sistema: é o montador quem troca a
     * chave pelo pseudônimo.
     */
    private static PapelDeTrabalho papelComChaveDeVerdade() {
        ChaveAcesso chave = new ChaveAcesso(CHAVE_DE_ACESSO);

        return new MontadorDePapelDeTrabalho(
                execucaoId -> List.of(achadoRegistrado(chave)),
                execucaoId -> List.of(new NaoAvaliadaRegistrada(
                        chave, 2, "RYY", "0.0.0-ficticia", "Motivo ficticio de teste.")),
                chaves -> Map.of(chave, new DadosDoDocumento(
                        chave, "99", "999", "111111", LocalDate.of(1900, 6, 15), Uf.SP)),
                new PseudonimizadorDeChaveComSal(pseudonimizador()))
                .montar(execucao());
    }

    private static Pseudonimizador pseudonimizador() {
        return new Pseudonimizador(new SalDeInstalacao(SAL_FICTICIO));
    }

    private static ExecucaoAuditoria execucao() {
        List<Achado> achados = new ArrayList<>();
        achados.add(achado(new ChaveAcesso(CHAVE_DE_ACESSO)));

        return ExecucaoAuditoria.de(
                UUID.fromString("00000000-0000-0000-0000-0000000000dd"),
                Instant.parse("1900-01-02T03:04:05Z"),
                "a".repeat(64),
                "catalogo-ficticio",
                "0.0-ficticia",
                1, 2, List.of("RXX", "RYY"), achados);
    }

    private static AchadoRegistrado achadoRegistrado(ChaveAcesso chave) {
        return new AchadoRegistrado(
                UUID.fromString("00000000-0000-0000-0000-0000000000ee"),
                achado(chave),
                new HashDoItem("e".repeat(64)),
                Optional.empty(),
                Instant.parse("1900-01-02T03:04:05Z"),
                Instant.parse("1900-01-02T03:04:05Z"));
    }

    private static Achado achado(ChaveAcesso chave) {
        return new Achado(
                "RXX",
                "0.0.0-ficticia",
                Severidade.GRAVE,
                chave,
                OptionalInt.of(1),
                List.of(new Evidencia(
                        "campoFicticio",
                        Optional.of("99,99"),
                        Optional.empty(),
                        new OrigemEvidencia.DaRegra("derivação fictícia para teste"))),
                "FUNDAMENTO FICTICIO PARA TESTE",
                PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1)),
                ValorEmRisco.naoCalculavel("motivo fictício de teste"));
    }
}
