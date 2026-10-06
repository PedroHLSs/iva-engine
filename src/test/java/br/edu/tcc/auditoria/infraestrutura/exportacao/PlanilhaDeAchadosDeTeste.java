package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.LinhaDeAchado;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoFicticio;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

// Auxiliar dos testes de 04/10/2026: gera a planilha com as linhas de achado pedidas e acha a célula pelo nome da coluna, para o teste não depender da posição.
final class PlanilhaDeAchadosDeTeste {

    private PlanilhaDeAchadosDeTeste() {
    }

    // Gera a planilha com os achados informados sobre o papel fictício e a devolve aberta.
    static Workbook gerar(Path pasta, List<LinhaDeAchado> achados) throws IOException {
        PapelDeTrabalho base = PapelDeTrabalhoFicticio.completo();
        PapelDeTrabalho papel = new PapelDeTrabalho(base.execucao(), achados, base.naoAvaliados(),
                base.motivosAgrupados(), base.itensNaoAvaliados(), Optional.of(List.of()), Optional.of(0),
                NaturezaDaCarga.naoDeclarada(), Optional.empty());
        Path destino = pasta.resolve("papel-" + System.nanoTime() + ".xlsx");
        new ExportadorXlsx(ZoneOffset.UTC).exportar(papel, destino);
        try (InputStream conteudo = Files.newInputStream(destino)) {
            return WorkbookFactory.create(conteudo);
        }
    }

    // A célula da aba Achados na linha do achado (a primeira é 0) e na coluna de nome informado.
    static Cell celula(Workbook planilha, int achado, String coluna) {
        Sheet aba = planilha.getSheet(ExportadorXlsx.ABA_ACHADOS);
        Row cabecalho = aba.getRow(ExportadorXlsx.LINHA_DO_CABECALHO);
        for (Cell titulo : cabecalho) {
            if (titulo.getStringCellValue().equals(coluna)) {
                Cell celula = aba.getRow(ExportadorXlsx.LINHA_DO_CABECALHO + 1 + achado).getCell(titulo.getColumnIndex());
                if (celula == null) {
                    throw new AssertionError("a coluna \"%s\" do achado %d não foi escrita".formatted(coluna, achado));
                }
                return celula;
            }
        }
        throw new AssertionError("a aba Achados não tem a coluna \"%s\"".formatted(coluna));
    }
}
