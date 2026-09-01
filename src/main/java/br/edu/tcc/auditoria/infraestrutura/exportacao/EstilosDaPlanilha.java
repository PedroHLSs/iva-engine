package br.edu.tcc.auditoria.infraestrutura.exportacao;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Os estilos de célula da planilha, criados uma vez por pasta de trabalho.
 *
 * <p>Uma vez, e não por célula: o Excel guarda estilos numa tabela global e tem
 * limite de 64 mil deles. Criar um estilo dentro do laço de linhas estoura esse
 * limite num acervo grande e deixa o arquivo gigante.</p>
 */
final class EstilosDaPlanilha {

    private final CellStyle titulo;
    private final CellStyle rotulo;
    private final CellStyle cabecalho;
    private final CellStyle texto;
    private final CellStyle textoLongo;
    private final CellStyle data;
    private final CellStyle dataHora;
    private final CellStyle inteiro;
    private final CellStyle monetario;

    EstilosDaPlanilha(Workbook planilha) {
        Font fonteDeTitulo = planilha.createFont();
        fonteDeTitulo.setBold(true);
        fonteDeTitulo.setFontHeightInPoints((short) 12);

        Font fonteEmNegrito = planilha.createFont();
        fonteEmNegrito.setBold(true);

        Font fonteBrancaEmNegrito = planilha.createFont();
        fonteBrancaEmNegrito.setBold(true);
        fonteBrancaEmNegrito.setColor(IndexedColors.WHITE.getIndex());

        this.titulo = planilha.createCellStyle();
        this.titulo.setFont(fonteDeTitulo);

        this.rotulo = planilha.createCellStyle();
        this.rotulo.setFont(fonteEmNegrito);
        this.rotulo.setVerticalAlignment(VerticalAlignment.TOP);

        this.cabecalho = planilha.createCellStyle();
        this.cabecalho.setFont(fonteBrancaEmNegrito);
        this.cabecalho.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        this.cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        this.cabecalho.setBorderBottom(BorderStyle.THIN);
        this.cabecalho.setVerticalAlignment(VerticalAlignment.CENTER);
        this.cabecalho.setWrapText(true);

        this.texto = planilha.createCellStyle();
        this.texto.setVerticalAlignment(VerticalAlignment.TOP);

        // Quebra de linha ligada: fundamento normativo, motivo e justificativa
        // são frases inteiras, e cortá-las na largura da coluna esconderia
        // justamente o que a pessoa precisa ler para conferir.
        this.textoLongo = planilha.createCellStyle();
        this.textoLongo.setVerticalAlignment(VerticalAlignment.TOP);
        this.textoLongo.setWrapText(true);

        this.data = planilha.createCellStyle();
        this.data.setVerticalAlignment(VerticalAlignment.TOP);
        this.data.setDataFormat(planilha.createDataFormat().getFormat("dd/mm/yyyy"));

        this.dataHora = planilha.createCellStyle();
        this.dataHora.setVerticalAlignment(VerticalAlignment.TOP);
        this.dataHora.setDataFormat(planilha.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));

        this.inteiro = planilha.createCellStyle();
        this.inteiro.setVerticalAlignment(VerticalAlignment.TOP);
        this.inteiro.setAlignment(HorizontalAlignment.RIGHT);
        this.inteiro.setDataFormat(planilha.createDataFormat().getFormat("0"));

        // Duas casas fixas: a planilha mostra centavo, e um valor em risco
        // arredondado para inteiro esconderia exatamente a diferença apontada.
        this.monetario = planilha.createCellStyle();
        this.monetario.setVerticalAlignment(VerticalAlignment.TOP);
        this.monetario.setAlignment(HorizontalAlignment.RIGHT);
        this.monetario.setDataFormat(planilha.createDataFormat().getFormat("#,##0.00"));
    }

    CellStyle titulo() {
        return titulo;
    }

    CellStyle rotulo() {
        return rotulo;
    }

    CellStyle cabecalho() {
        return cabecalho;
    }

    CellStyle texto() {
        return texto;
    }

    CellStyle textoLongo() {
        return textoLongo;
    }

    CellStyle data() {
        return data;
    }

    CellStyle dataHora() {
        return dataHora;
    }

    CellStyle inteiro() {
        return inteiro;
    }

    CellStyle monetario() {
        return monetario;
    }
}
