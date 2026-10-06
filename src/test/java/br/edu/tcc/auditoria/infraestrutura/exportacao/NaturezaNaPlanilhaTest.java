package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoFicticio;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// D021 (04/10/2026): a planilha marca o catálogo fictício tão visivelmente quanto a tela — a faixa na primeira linha de toda aba, com destaque, e a natureza na identificação. Com catálogo normativo, nada nela fala em fictício. Antes, uma planilha de catálogo inteiramente fictício não tinha nenhuma ocorrência de "fictício", "natureza" ou "procedência".
class NaturezaNaPlanilhaTest {

    private static final Optional<Natureza> F = Optional.of(Natureza.FICTICIO);
    private static final Optional<Natureza> N = Optional.of(Natureza.NORMATIVO);

    @TempDir
    Path pasta;

    @Test
    void catalogoFicticioDeveSerMarcadoEmTodaAba() throws IOException {
        try (Workbook planilha = gerar(new NaturezaDaCarga(F, F, F, F, F, F))) {
            for (Sheet aba : planilha) {
                Cell faixa = aba.getRow(0).getCell(0);
                assertThat(faixa.getStringCellValue()).as("faixa da aba %s", aba.getSheetName())
                        .startsWith("DADOS DE DEMONSTRAÇÃO");
                assertThat(faixa.getCellStyle().getFillPattern()).as("destaque da faixa da aba %s", aba.getSheetName())
                        .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            }
            assertThat(valorAoLadoDe(planilha.getSheet(ExportadorXlsx.ABA_RESUMO), ExportadorXlsx.ROTULO_DA_NATUREZA))
                    .isEqualTo("Dados de demonstração");
            assertThat(texto(planilha)).contains("fictícias").contains("natureza");
        }
    }

    @Test
    void catalogoNormativoNaoDeveFalarEmFicticio() throws IOException {
        try (Workbook planilha = gerar(new NaturezaDaCarga(N, N, N, N, N, N))) {
            String texto = texto(planilha);
            assertThat(texto).doesNotContain("fictíci").doesNotContain("demonstração");
            for (Sheet aba : planilha) {
                assertThat(aba.getRow(0).getCell(0).getStringCellValue()).startsWith("CATÁLOGO NORMATIVO");
            }
            assertThat(valorAoLadoDe(planilha.getSheet(ExportadorXlsx.ABA_RESUMO), ExportadorXlsx.ROTULO_DA_NATUREZA))
                    .isEqualTo("Catálogo normativo");
        }
    }

    // Misto: a planilha diz quais tabelas são fictícias, inclusive a cobertura, e não lista as normativas.
    @Test
    void catalogoMistoDeveDizerQuaisTabelasSaoFicticias() throws IOException {
        try (Workbook planilha = gerar(new NaturezaDaCarga(N, N, F, N, N, F))) {
            Sheet resumo = planilha.getSheet(ExportadorXlsx.ABA_RESUMO);
            assertThat(valorAoLadoDe(resumo, ExportadorXlsx.ROTULO_DAS_FICTICIAS)).isEqualTo("ITEM_ANEXO, COBERTURA");
            for (Sheet aba : planilha) {
                assertThat(aba.getRow(0).getCell(0).getStringCellValue())
                        .startsWith("CATÁLOGO PARCIALMENTE FICTÍCIO")
                        .contains("ITEM_ANEXO, COBERTURA")
                        .doesNotContain("CLASSIFICACAO_TRIBUTARIA");
            }
        }
    }

    // Carga anterior a esta decisão: a cobertura sem natureza não deixa a planilha dizer "normativo".
    @Test
    void coberturaSemNaturezaDeveSairNaoDeclarada() throws IOException {
        try (Workbook planilha = gerar(new NaturezaDaCarga(N, N, N, N, N))) {
            Sheet resumo = planilha.getSheet(ExportadorXlsx.ABA_RESUMO);
            assertThat(valorAoLadoDe(resumo, ExportadorXlsx.ROTULO_DA_NATUREZA)).isEqualTo("Procedência não declarada");
            assertThat(valorAoLadoDe(resumo, ExportadorXlsx.ROTULO_DAS_SEM_NATUREZA)).isEqualTo("COBERTURA");
        }
    }

    private Workbook gerar(NaturezaDaCarga natureza) throws IOException {
        PapelDeTrabalho base = PapelDeTrabalhoFicticio.completo();
        PapelDeTrabalho papel = new PapelDeTrabalho(base.execucao(), base.achados(), base.naoAvaliados(),
                base.motivosAgrupados(), base.itensNaoAvaliados(), Optional.of(List.of()), Optional.of(0), natureza, Optional.empty());
        Path destino = pasta.resolve("papel-" + System.nanoTime() + ".xlsx");
        new ExportadorXlsx(ZoneOffset.UTC).exportar(papel, destino);
        try (InputStream conteudo = Files.newInputStream(destino)) {
            return WorkbookFactory.create(conteudo);
        }
    }

    // Todo o texto da planilha, em minúsculas.
    private static String texto(Workbook planilha) {
        StringBuilder texto = new StringBuilder();
        for (Sheet aba : planilha) {
            for (Row linha : aba) {
                for (Cell celula : linha) {
                    if (celula.getCellType() == CellType.STRING) {
                        texto.append(celula.getStringCellValue()).append('\n');
                    }
                }
            }
        }
        return texto.toString().toLowerCase(Locale.ROOT);
    }

    private static String valorAoLadoDe(Sheet aba, String rotulo) {
        for (Row linha : aba) {
            Cell primeira = linha.getCell(0);
            if (primeira != null && primeira.getCellType() == CellType.STRING
                    && primeira.getStringCellValue().equals(rotulo)) {
                return linha.getCell(1).getStringCellValue();
            }
        }
        throw new AssertionError("a aba %s não tem a linha \"%s\"".formatted(aba.getSheetName(), rotulo));
    }
}
