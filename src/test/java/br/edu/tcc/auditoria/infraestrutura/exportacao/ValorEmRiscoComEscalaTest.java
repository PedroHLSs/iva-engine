package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.LinhaDeAchado;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoFicticio;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.StatusDeTratativa;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Correção de 04/10/2026 (D025): o valor em risco chega à célula como BigDecimal e sai na escala declarada. Até essa data o formato era fixo em duas casas e 7,11100 aparecia 7,11. Valor que não cabe exato num double vai como texto, para a planilha nunca arredondar em silêncio. Valores fictícios.
class ValorEmRiscoComEscalaTest {

    private static final String COLUNA = "Valor em risco";

    @TempDir
    Path pasta;

    @Test
    void deveSerNumeroNaEscalaDeclarada() throws IOException {
        try (Workbook planilha = PlanilhaDeAchadosDeTeste.gerar(pasta, List.of(achadoCom(new BigDecimal("7.11100")), achadoCom(new BigDecimal("99.99"))))) {
            Cell celula = PlanilhaDeAchadosDeTeste.celula(planilha, 0, COLUNA);

            assertThat(celula.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(BigDecimal.valueOf(celula.getNumericCellValue())).isEqualByComparingTo("7.111");
            assertThat(celula.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00000");
            assertThat(new DataFormatter(Locale.US).formatCellValue(celula)).isEqualTo("7.11100");
        }
    }

    // Duas escalas na mesma aba, cada uma com a sua.
    @Test
    void cadaValorDeveManterASuaEscala() throws IOException {
        try (Workbook planilha = PlanilhaDeAchadosDeTeste.gerar(pasta,
                List.of(achadoCom(new BigDecimal("99.99")), achadoCom(new BigDecimal("0.123"))))) {
            DataFormatter formato = new DataFormatter(Locale.US);

            assertThat(formato.formatCellValue(PlanilhaDeAchadosDeTeste.celula(planilha, 0, COLUNA))).isEqualTo("99.99");
            assertThat(formato.formatCellValue(PlanilhaDeAchadosDeTeste.celula(planilha, 1, COLUNA))).isEqualTo("0.123");
        }
    }

    @Test
    void valorQueNaoCabeNumDoubleDeveSairExatoComoTexto() throws IOException {
        BigDecimal grande = new BigDecimal("99999999999999.99999");
        try (Workbook planilha = PlanilhaDeAchadosDeTeste.gerar(pasta, List.of(achadoCom(grande), achadoCom(new BigDecimal("99.99"))))) {
            Cell celula = PlanilhaDeAchadosDeTeste.celula(planilha, 0, COLUNA);

            assertThat(celula.getCellType()).isEqualTo(CellType.STRING);
            assertThat(celula.getStringCellValue()).isEqualTo("99999999999999.99999");
        }
    }

    // O papel fictício confere que a execução tem dois apontamentos (D019): todo teste passa dois.
    static LinhaDeAchado achadoCom(BigDecimal valorEmRisco) {
        return new LinhaDeAchado(
                PapelDeTrabalhoFicticio.PSEUDONIMO_PRIMEIRO, "99", "999", "111111",
                PapelDeTrabalhoFicticio.DATA_EMISSAO, Uf.SP, 1,
                PapelDeTrabalhoFicticio.REGRA_GRAVE, PapelDeTrabalhoFicticio.VERSAO_DA_REGRA, Severidade.GRAVE,
                List.of("valorIbsUf"), List.of(Optional.of("8.88")), List.of(Optional.of("9.99")),
                PapelDeTrabalhoFicticio.FUNDAMENTO, PapelDeTrabalhoFicticio.VIGENCIA_INICIO, Optional.empty(),
                Optional.of(valorEmRisco), Optional.empty(),
                StatusDeTratativa.ABERTO, Optional.empty(), Optional.empty());
    }
}
