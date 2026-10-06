package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoFicticio;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Correção de 04/10/2026 (D025): apontamento sem tratativa escreve a ausência em "Justificativa" e em "Tratado em", como a D007 manda. Até essa data as duas células saíam em branco, indistinguíveis de célula esquecida. O segundo achado do papel fictício está em aberto.
class TratativaAusenteEscritaTest {

    @TempDir
    Path pasta;

    @Test
    void justificativaETratadoEmDevemDizerQueNaoHaTratativa() throws IOException {
        try (Workbook planilha = PlanilhaDeAchadosDeTeste.gerar(pasta, PapelDeTrabalhoFicticio.achados())) {
            for (String coluna : new String[] {"Justificativa", "Tratado em"}) {
                Cell celula = PlanilhaDeAchadosDeTeste.celula(planilha, 1, coluna);
                assertThat(celula.getCellType()).as(coluna).isEqualTo(CellType.STRING);
                assertThat(celula.getStringCellValue()).as(coluna).isEqualTo(ExportadorXlsx.SEM_TRATATIVA);
            }
        }
    }

    // O lado presente continua igual: a justificativa e a data do achado tratado.
    @Test
    void achadoTratadoDeveContinuarComJustificativaEData() throws IOException {
        try (Workbook planilha = PlanilhaDeAchadosDeTeste.gerar(pasta, PapelDeTrabalhoFicticio.achados())) {
            assertThat(PlanilhaDeAchadosDeTeste.celula(planilha, 0, "Justificativa").getStringCellValue())
                    .isEqualTo(ExportadorXlsx.JUSTIFICATIVA_OMITIDA);
            assertThat(PlanilhaDeAchadosDeTeste.celula(planilha, 0, "Tratado em").getCellType())
                    .isEqualTo(CellType.NUMERIC);
        }
    }

    // Nenhuma célula das linhas de achado fica em branco.
    @Test
    void nenhumaCelulaDeAchadoDeveFicarEmBranco() throws IOException {
        try (Workbook planilha = PlanilhaDeAchadosDeTeste.gerar(pasta, PapelDeTrabalhoFicticio.achados())) {
            var aba = planilha.getSheet(ExportadorXlsx.ABA_ACHADOS);
            var cabecalho = aba.getRow(ExportadorXlsx.LINHA_DO_CABECALHO);
            for (int achado = 0; achado < 2; achado++) {
                for (Cell titulo : cabecalho) {
                    Cell celula = PlanilhaDeAchadosDeTeste.celula(planilha, achado, titulo.getStringCellValue());
                    boolean branca = celula.getCellType() == CellType.BLANK
                            || (celula.getCellType() == CellType.STRING && celula.getStringCellValue().isBlank());
                    assertThat(branca).as("achado %d, coluna %s", achado, titulo.getStringCellValue()).isFalse();
                }
            }
        }
    }
}
