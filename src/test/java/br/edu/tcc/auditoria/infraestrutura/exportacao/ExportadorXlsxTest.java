package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoFicticio;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoInvalido;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gera a planilha a partir de um papel de trabalho conhecido e confere células
 * específicas.
 *
 * <p>Os índices de linha estão escritos à mão de propósito. O leiaute do Resumo
 * é parte do que a etapa entrega — a identificação da execução tem de estar no
 * topo, sem nada acima —, e um teste que procurasse os rótulos por varredura
 * deixaria de reclamar se alguém empurrasse o bloco para o meio da aba.</p>
 */
class ExportadorXlsxTest {

    // Leiaute da aba Resumo, em índices de linha começando em zero.
    // D021 (04/10/2026): a faixa de procedência ocupa a primeira linha de toda aba, e tudo desceu uma. No Resumo,
    // a natureza do catálogo e as tabelas sem natureza declarada entraram abaixo da versão do catálogo — o papel
    // fictício usa a aridade antiga, que fica com a natureza não declarada.
    private static final int LINHA_DA_FAIXA = 0;
    private static final int LINHA_DO_TITULO = 1;
    private static final int LINHA_DO_ROTULO_DE_IDENTIFICACAO = 3;
    private static final int LINHA_DA_EXECUCAO = 4;
    private static final int LINHA_DA_DATA_HORA = 5;
    private static final int LINHA_DA_VERSAO_DO_CATALOGO = 6;
    private static final int LINHA_DA_NATUREZA = 7;
    private static final int LINHA_DA_VERSAO_DAS_REGRAS = 9;
    // D023 (04/10/2026): a tolerância da R05 entrou abaixo da versão das regras, e as seguintes desceram uma.
    private static final int LINHA_DA_TOLERANCIA = 10;
    private static final int LINHA_DO_HASH_DE_ENTRADA = 11;
    private static final int LINHA_DE_DOCUMENTOS = 12;
    private static final int LINHA_DE_ITENS = 13;
    // D018 (04/10/2026): a linha dos arquivos não lidos entrou logo abaixo dos itens, e as seguintes desceram uma.
    // D019 (04/10/2026): a dos documentos repetidos entrou abaixo dela, e as seguintes desceram mais uma.
    private static final int LINHA_DOS_NAO_LIDOS = 14;
    private static final int LINHA_DOS_REPETIDOS = 15;
    private static final int LINHA_DA_PRIMEIRA_SEVERIDADE = 19;
    private static final int LINHA_DO_TOTAL_DE_ACHADOS = 23;
    private static final int LINHA_DA_PRIMEIRA_REGRA = 27;
    private static final int LINHA_DE_NAO_CONCLUIDAS = 33;
    private static final int LINHA_DE_ITENS_ATINGIDOS = 34;
    private static final int LINHA_DO_PRIMEIRO_MOTIVO = 37;

    // D021: nas abas de linhas, o cabeçalho fica abaixo da faixa.
    private static final int CABECALHO = ExportadorXlsx.LINHA_DO_CABECALHO;

    // Colunas da aba Achados.
    private static final int COLUNA_DOCUMENTO = 0;
    private static final int COLUNA_NUMERO = 3;
    private static final int COLUNA_EMISSAO = 4;
    private static final int COLUNA_UF = 5;
    private static final int COLUNA_ITEM = 6;
    private static final int COLUNA_REGRA = 7;
    private static final int COLUNA_SEVERIDADE = 9;
    private static final int COLUNA_CAMPO = 10;
    private static final int COLUNA_ENCONTRADO = 11;
    private static final int COLUNA_ESPERADO = 12;
    private static final int COLUNA_FUNDAMENTO = 13;
    private static final int COLUNA_VIGENCIA_DE = 14;
    private static final int COLUNA_VIGENCIA_ATE = 15;
    private static final int COLUNA_VALOR_EM_RISCO = 16;
    private static final int COLUNA_TRATATIVA = 17;
    private static final int COLUNA_JUSTIFICATIVA = 18;

    @TempDir
    private Path pasta;

    private Path arquivo;
    private Workbook gerada;

    @BeforeEach
    void gerarPlanilha() throws IOException {
        arquivo = pasta.resolve("papel-de-trabalho.xlsx");
        // Fuso fixo: a coluna de data e hora não pode depender de onde o teste roda.
        // Etapa 12: a justificativa passou a ser opt-in; este teste confere a planilha com ela ligada.
        new ExportadorXlsx(ZoneOffset.UTC, true).exportar(PapelDeTrabalhoFicticio.completo(), arquivo);
        gerada = abrir(arquivo);
    }

    /**
     * Abre a planilha por fluxo, e não pelo arquivo.
     *
     * <p>{@code WorkbookFactory.create(File)} mantém o arquivo aberto para
     * escrita e tenta regravá-lo no fechamento, o que faz qualquer teste que
     * sobrescreva o mesmo caminho falhar. Lendo por fluxo, o conteúdo vem para a
     * memória e o arquivo fica livre.</p>
     */
    private static Workbook abrir(Path caminho) throws IOException {
        try (InputStream conteudo = Files.newInputStream(caminho)) {
            return WorkbookFactory.create(conteudo);
        }
    }

    @AfterEach
    void fecharPlanilha() throws IOException {
        if (gerada != null) {
            gerada.close();
        }
    }

    @Test
    void deveGravarOArquivoNoDestino() {
        assertThat(arquivo).exists();
        assertThat(arquivo.toFile().length()).isPositive();
    }

    @Test
    void deveTerAsQuatroAbasNaOrdem() {
        assertThat(gerada.getNumberOfSheets()).isEqualTo(4);
        assertThat(gerada.getSheetName(0)).isEqualTo(ExportadorXlsx.ABA_RESUMO);
        assertThat(gerada.getSheetName(1)).isEqualTo(ExportadorXlsx.ABA_ACHADOS);
        assertThat(gerada.getSheetName(2)).isEqualTo(ExportadorXlsx.ABA_NAO_AVALIADOS);
        assertThat(gerada.getSheetName(3)).isEqualTo(ExportadorXlsx.ABA_NAO_LIDOS);
    }

    // D018: o papel de trabalho fictício não registra a leitura, e a planilha diz isso em vez de zero.
    @Test
    void deveDizerQueALeituraNaoFoiRegistradaEmVezDeZero() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);
        assertThat(texto(resumo, LINHA_DOS_NAO_LIDOS, 0)).isEqualTo(ExportadorXlsx.ROTULO_DOS_NAO_LIDOS);
        assertThat(texto(resumo, LINHA_DOS_NAO_LIDOS, 1)).isEqualTo(ExportadorXlsx.LEITURA_NAO_REGISTRADA);
        assertThat(texto(gerada.getSheet(ExportadorXlsx.ABA_NAO_LIDOS), CABECALHO + 1, 0))
                .isEqualTo(ExportadorXlsx.LEITURA_NAO_REGISTRADA);
    }

    // D021: a faixa abre o Resumo, e a natureza do catálogo está na identificação. O papel fictício usa a aridade antiga, que fica com a procedência não declarada.
    @Test
    void deveAbrirComAFaixaEDizerANaturezaNaIdentificacao() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);
        assertThat(texto(resumo, LINHA_DA_FAIXA, 0)).startsWith("PROCEDÊNCIA NÃO DECLARADA");
        assertThat(texto(resumo, LINHA_DA_NATUREZA, 0)).isEqualTo(ExportadorXlsx.ROTULO_DA_NATUREZA);
        assertThat(texto(resumo, LINHA_DA_NATUREZA, 1)).isEqualTo("Procedência não declarada");
    }

    // D023: sem a tolerância registrada — o papel fictício usa a aridade antiga —, a planilha diz isso em vez de um valor.
    @Test
    void deveDizerQueAToleranciaNaoFoiRegistradaEmVezDeUmValor() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);
        assertThat(texto(resumo, LINHA_DA_TOLERANCIA, 0)).isEqualTo(ExportadorXlsx.ROTULO_DA_TOLERANCIA);
        assertThat(texto(resumo, LINHA_DA_TOLERANCIA, 1)).startsWith("(não registrada");
    }

    // D019: sem a contagem de documentos repetidos registrada, a planilha diz isso em vez de zero.
    @Test
    void deveDizerQueOsRepetidosNaoForamRegistradosEmVezDeZero() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);
        assertThat(texto(resumo, LINHA_DOS_REPETIDOS, 0)).isEqualTo(ExportadorXlsx.ROTULO_DOS_REPETIDOS);
        assertThat(texto(resumo, LINHA_DOS_REPETIDOS, 1)).isEqualTo(ExportadorXlsx.REPETIDOS_NAO_REGISTRADOS);
    }

    @Test
    void deveAbrirOResumoComAIdentificacaoDaExecucao() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);

        assertThat(texto(resumo, LINHA_DO_TITULO, 0))
                .isEqualTo("Papel de trabalho — auditoria de coerência de IBS/CBS");
        assertThat(texto(resumo, LINHA_DO_ROTULO_DE_IDENTIFICACAO, 0))
                .as("o bloco de identificação abre a planilha: é o que a torna conferível depois")
                .isEqualTo("Identificação da execução");

        assertThat(texto(resumo, LINHA_DA_EXECUCAO, 0)).isEqualTo("Execução");
        assertThat(texto(resumo, LINHA_DA_EXECUCAO, 1))
                .isEqualTo(PapelDeTrabalhoFicticio.EXECUCAO.toString());

        assertThat(texto(resumo, LINHA_DA_VERSAO_DO_CATALOGO, 0)).isEqualTo("Versão do catálogo");
        assertThat(texto(resumo, LINHA_DA_VERSAO_DO_CATALOGO, 1))
                .isEqualTo(PapelDeTrabalhoFicticio.VERSAO_CATALOGO);

        assertThat(texto(resumo, LINHA_DA_VERSAO_DAS_REGRAS, 0))
                .isEqualTo("Versão do conjunto de regras");
        assertThat(texto(resumo, LINHA_DA_VERSAO_DAS_REGRAS, 1))
                .isEqualTo(PapelDeTrabalhoFicticio.VERSAO_REGRAS);

        assertThat(texto(resumo, LINHA_DO_HASH_DE_ENTRADA, 0)).isEqualTo("Resumo da entrada");
        assertThat(texto(resumo, LINHA_DO_HASH_DE_ENTRADA, 1))
                .isEqualTo(PapelDeTrabalhoFicticio.HASH_DE_ENTRADA);
    }

    @Test
    void deveGravarADataDaExecucaoComoDataEHoraDeVerdade() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);

        assertThat(texto(resumo, LINHA_DA_DATA_HORA, 0)).isEqualTo("Data e hora");
        assertThat(celula(resumo, LINHA_DA_DATA_HORA, 1).getLocalDateTimeCellValue())
                .as("data gravada como número, e não como texto: quem confere ordena e filtra por ela")
                .isEqualTo(LocalDateTime.of(1900, 1, 2, 3, 4, 5));
    }

    @Test
    void deveGravarAsQuantidadesDaExecucao() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);

        assertThat(texto(resumo, LINHA_DE_DOCUMENTOS, 0)).isEqualTo("Documentos auditados");
        assertThat(numero(resumo, LINHA_DE_DOCUMENTOS, 1)).isEqualTo(PapelDeTrabalhoFicticio.DOCUMENTOS);
        assertThat(numero(resumo, LINHA_DE_ITENS, 1)).isEqualTo(PapelDeTrabalhoFicticio.ITENS);
    }

    @Test
    void deveContarApontamentosPorSeveridadeIncluindoZero() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);

        assertThat(texto(resumo, LINHA_DA_PRIMEIRA_SEVERIDADE, 0)).isEqualTo("CRITICA");
        assertThat(numero(resumo, LINHA_DA_PRIMEIRA_SEVERIDADE, 1)).isEqualTo(1);
        assertThat(texto(resumo, LINHA_DA_PRIMEIRA_SEVERIDADE + 1, 0)).isEqualTo("GRAVE");
        assertThat(numero(resumo, LINHA_DA_PRIMEIRA_SEVERIDADE + 1, 1)).isEqualTo(1);

        assertThat(texto(resumo, LINHA_DA_PRIMEIRA_SEVERIDADE + 3, 0)).isEqualTo("INFORMATIVA");
        assertThat(numero(resumo, LINHA_DA_PRIMEIRA_SEVERIDADE + 3, 1))
                .as("severidade sem apontamento aparece zerada, e não some da planilha")
                .isZero();

        assertThat(texto(resumo, LINHA_DO_TOTAL_DE_ACHADOS, 0)).isEqualTo("Total de apontamentos");
        assertThat(numero(resumo, LINHA_DO_TOTAL_DE_ACHADOS, 1)).isEqualTo(2);
    }

    @Test
    void deveContarApontamentosPorRegraIncluindoAQueNadaApontou() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);

        assertThat(texto(resumo, LINHA_DA_PRIMEIRA_REGRA, 0))
                .isEqualTo(PapelDeTrabalhoFicticio.REGRA_CRITICA);
        assertThat(numero(resumo, LINHA_DA_PRIMEIRA_REGRA, 1)).isEqualTo(1);

        assertThat(texto(resumo, LINHA_DA_PRIMEIRA_REGRA + 2, 0))
                .isEqualTo(PapelDeTrabalhoFicticio.REGRA_SEM_ACHADO);
        assertThat(numero(resumo, LINHA_DA_PRIMEIRA_REGRA + 2, 1))
                .as("regra que rodou e nada encontrou aparece com zero")
                .isZero();
    }

    @Test
    void deveResumirOsNaoAvaliadosComOsMotivosAgrupados() {
        Sheet resumo = gerada.getSheet(ExportadorXlsx.ABA_RESUMO);

        assertThat(texto(resumo, LINHA_DE_NAO_CONCLUIDAS, 0)).isEqualTo("Avaliações não concluídas");
        assertThat(numero(resumo, LINHA_DE_NAO_CONCLUIDAS, 1)).isEqualTo(3);
        assertThat(texto(resumo, LINHA_DE_ITENS_ATINGIDOS, 0)).isEqualTo("Itens atingidos");
        assertThat(numero(resumo, LINHA_DE_ITENS_ATINGIDOS, 1)).isEqualTo(2);

        assertThat(texto(resumo, LINHA_DO_PRIMEIRO_MOTIVO, 0))
                .isEqualTo(PapelDeTrabalhoFicticio.REGRA_SEM_ACHADO);
        assertThat(texto(resumo, LINHA_DO_PRIMEIRO_MOTIVO, 1))
                .isEqualTo(PapelDeTrabalhoFicticio.MOTIVO_REPETIDO);
        assertThat(numero(resumo, LINHA_DO_PRIMEIRO_MOTIVO, 2))
                .as("o motivo mais frequente vem primeiro: é o que, resolvido, elimina mais linhas")
                .isEqualTo(2);
        assertThat(numero(resumo, LINHA_DO_PRIMEIRO_MOTIVO + 1, 2)).isEqualTo(1);
    }

    @Test
    void deveEscreverUmaLinhaPorAchadoComOCabecalhoNoTopo() {
        Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);

        assertThat(texto(achados, CABECALHO, COLUNA_DOCUMENTO)).isEqualTo("Documento (pseudônimo)");
        assertThat(texto(achados, CABECALHO, COLUNA_FUNDAMENTO)).isEqualTo("Fundamento normativo");
        assertThat(achados.getLastRowNum())
                .as("duas linhas de achado depois do cabeçalho")
                .isEqualTo(CABECALHO + 2);
    }

    @Test
    void devePermitirRastrearOAchadoAteODocumentoEODispositivoLegal() {
        Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);
        int primeiro = CABECALHO + 1;

        assertThat(texto(achados, primeiro, COLUNA_DOCUMENTO))
                .isEqualTo(PapelDeTrabalhoFicticio.PSEUDONIMO_PRIMEIRO);
        assertThat(texto(achados, primeiro, COLUNA_NUMERO)).isEqualTo("111111");
        assertThat(celula(achados, primeiro, COLUNA_EMISSAO).getLocalDateTimeCellValue().toLocalDate())
                .isEqualTo(PapelDeTrabalhoFicticio.DATA_EMISSAO);
        assertThat(texto(achados, primeiro, COLUNA_UF)).isEqualTo("SP");
        assertThat(numero(achados, primeiro, COLUNA_ITEM)).isEqualTo(1);
        assertThat(texto(achados, primeiro, COLUNA_REGRA))
                .isEqualTo(PapelDeTrabalhoFicticio.REGRA_CRITICA);
        assertThat(texto(achados, primeiro, COLUNA_SEVERIDADE)).isEqualTo("CRITICA");
        assertThat(texto(achados, primeiro, COLUNA_FUNDAMENTO))
                .as("sem o dispositivo legal na linha, conferir exige abrir o sistema")
                .isEqualTo(PapelDeTrabalhoFicticio.FUNDAMENTO);
        assertThat(celula(achados, primeiro, COLUNA_VIGENCIA_DE)
                .getLocalDateTimeCellValue().toLocalDate())
                .isEqualTo(PapelDeTrabalhoFicticio.VIGENCIA_INICIO);
        assertThat(celula(achados, primeiro, COLUNA_VIGENCIA_ATE)
                .getLocalDateTimeCellValue().toLocalDate())
                .isEqualTo(PapelDeTrabalhoFicticio.VIGENCIA_FIM);
    }

    @Test
    void deveAlinharAsEvidenciasLinhaALinhaDentroDaCelula() {
        Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);
        int primeiro = CABECALHO + 1;

        assertThat(texto(achados, primeiro, COLUNA_CAMPO)).isEqualTo("cClassTrib\ncstIbs");
        assertThat(texto(achados, primeiro, COLUNA_ENCONTRADO))
                .as("o campo que não veio no documento é dito, não deixado em branco")
                .isEqualTo("999999\n" + Celulas.NAO_INFORMADO);
        assertThat(texto(achados, primeiro, COLUNA_ESPERADO))
                .as("a regra sem valor de referência a opor também é dita")
                .isEqualTo(Celulas.SEM_REFERENCIA + "\nAAA");
    }

    @Test
    void deveEscreverValorEmRiscoComoNumeroEOMotivoQuandoNaoHaValor() {
        Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);

        assertThat(numero(achados, CABECALHO + 1, COLUNA_VALOR_EM_RISCO))
                .isEqualTo(PapelDeTrabalhoFicticio.VALOR_EM_RISCO.doubleValue());
        assertThat(texto(achados, CABECALHO + 2, COLUNA_VALOR_EM_RISCO))
                .as("sem montante, a célula traz o motivo — nunca fica vazia sem explicação")
                .isEqualTo(PapelDeTrabalhoFicticio.MOTIVO_DO_VALOR_AUSENTE);
    }

    @Test
    void deveMostrarOStatusDeTratativaEAJustificativa() {
        Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);

        assertThat(texto(achados, CABECALHO + 1, COLUNA_TRATATIVA)).isEqualTo("ACEITO");
        assertThat(texto(achados, CABECALHO + 1, COLUNA_JUSTIFICATIVA))
                .isEqualTo(PapelDeTrabalhoFicticio.JUSTIFICATIVA);

        assertThat(texto(achados, CABECALHO + 2, COLUNA_TRATATIVA))
                .as("apontamento sem decisão aparece como ABERTO, e não como célula vazia")
                .isEqualTo("ABERTO");
    }

    @Test
    void deveDizerQuandoAVigenciaNaoTemFim() {
        Sheet achados = gerada.getSheet(ExportadorXlsx.ABA_ACHADOS);

        assertThat(texto(achados, CABECALHO + 2, COLUNA_VIGENCIA_ATE)).isEqualTo(Celulas.SEM_FIM_DECLARADO);
    }

    @Test
    void deveEscreverUmaLinhaPorAvaliacaoNaoConcluidaComOMotivo() {
        Sheet naoAvaliados = gerada.getSheet(ExportadorXlsx.ABA_NAO_AVALIADOS);

        assertThat(texto(naoAvaliados, CABECALHO, 7)).isEqualTo("Motivo");
        assertThat(naoAvaliados.getLastRowNum()).isEqualTo(CABECALHO + 3);
        assertThat(texto(naoAvaliados, CABECALHO + 1, 0)).isEqualTo(PapelDeTrabalhoFicticio.PSEUDONIMO_PRIMEIRO);
        assertThat(numero(naoAvaliados, CABECALHO + 1, 4)).isEqualTo(1);
        assertThat(texto(naoAvaliados, CABECALHO + 1, 7)).isEqualTo(PapelDeTrabalhoFicticio.MOTIVO_REPETIDO);
        assertThat(texto(naoAvaliados, CABECALHO + 3, 7)).isEqualTo(PapelDeTrabalhoFicticio.MOTIVO_UNICO);
    }

    @Test
    void deveGerarPlanilhaValidaQuandoNaoHouveAchadoNemNaoAvaliado() throws IOException {
        Path vazia = pasta.resolve("sem-nada.xlsx");
        new ExportadorXlsx(ZoneOffset.UTC).exportar(PapelDeTrabalhoFicticio.semNada(), vazia);

        try (Workbook planilha = abrir(vazia)) {
            assertThat(planilha.getNumberOfSheets()).isEqualTo(4);
            assertThat(planilha.getSheet(ExportadorXlsx.ABA_ACHADOS).getLastRowNum())
                    .as("só a faixa e o cabeçalho")
                    .isEqualTo(CABECALHO);
            assertThat(texto(planilha.getSheet(ExportadorXlsx.ABA_RESUMO),
                    LINHA_DO_HASH_DE_ENTRADA, 1))
                    .as("lote sem apontamento continua tendo identificação de execução")
                    .isEqualTo(PapelDeTrabalhoFicticio.HASH_DE_ENTRADA);
        }
    }

    @Test
    void deveCriarAPastaDeDestinoQuandoElaNaoExiste() {
        Path aninhado = pasta.resolve("relatorios").resolve("2026").resolve("papel.xlsx");

        new ExportadorXlsx(ZoneOffset.UTC).exportar(PapelDeTrabalhoFicticio.completo(), aninhado);

        assertThat(aninhado).exists();
    }

    @Test
    void deveSubstituirPlanilhaAnteriorNoMesmoCaminho() throws IOException {
        long tamanhoAnterior = Files.size(arquivo);

        new ExportadorXlsx(ZoneOffset.UTC).exportar(PapelDeTrabalhoFicticio.semNada(), arquivo);

        assertThat(Files.size(arquivo)).isNotEqualTo(tamanhoAnterior);
    }

    @Test
    void deveRecusarPapelOuDestinoAusente() {
        ExportadorXlsx exportador = new ExportadorXlsx(ZoneOffset.UTC);

        assertThatThrownBy(() -> exportador.exportar(null, arquivo))
                .isInstanceOf(PapelDeTrabalhoInvalido.class);
        assertThatThrownBy(() -> exportador.exportar(PapelDeTrabalhoFicticio.completo(), null))
                .isInstanceOf(PapelDeTrabalhoInvalido.class);
    }

    private static Cell celula(Sheet aba, int linha, int coluna) {
        Row row = aba.getRow(linha);
        assertThat(row).as("linha %d da aba \"%s\"", linha, aba.getSheetName()).isNotNull();
        Cell cell = row.getCell(coluna);
        assertThat(cell)
                .as("célula (%d, %d) da aba \"%s\"", linha, coluna, aba.getSheetName())
                .isNotNull();
        return cell;
    }

    private static String texto(Sheet aba, int linha, int coluna) {
        return celula(aba, linha, coluna).getStringCellValue();
    }

    private static double numero(Sheet aba, int linha, int coluna) {
        return celula(aba, linha, coluna).getNumericCellValue();
    }
}
