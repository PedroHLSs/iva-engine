package br.edu.tcc.auditoria.infraestrutura.exportacao;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

// Classe que escreve as células da planilha. Campo que não veio é escrito com um texto, e nunca fica em branco, porque célula vazia não se distingue de célula esquecida; zero continua sendo o número zero.
final class Celulas {

    // Texto escrito onde a nota não declarou o campo.
    static final String NAO_INFORMADO = "(não informado)";

    // Texto escrito onde a regra não tinha valor de referência.
    static final String SEM_REFERENCIA = "(sem referência)";

    // Texto escrito onde a vigência não tem data de fim.
    static final String SEM_FIM_DECLARADO = "(sem fim declarado)";

    // Separador das evidências dentro de uma célula.
    private static final String QUEBRA = "\n";

    private final ZoneId fusoDeApresentacao;

    // Construtor que recebe o fuso em que data e hora são mostradas.
    Celulas(ZoneId fusoDeApresentacao) {
        this.fusoDeApresentacao = fusoDeApresentacao;
    }

    // Método estático que escreve texto na célula.
    static void texto(Row linha, int coluna, String valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    // Método estático que escreve um número inteiro na célula.
    static void inteiro(Row linha, int coluna, long valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    // Método estático que escreve uma data na célula.
    static void data(Row linha, int coluna, LocalDate valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    // Escreve data e hora na célula, convertidas para o fuso de apresentação.
    void dataHora(Row linha, int coluna, Instant valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(LocalDateTime.ofInstant(valor, fusoDeApresentacao));
        celula.setCellStyle(estilo);
    }

    // Método estático que escreve o valor em risco como número, ou o motivo de não haver valor como texto; nunca deixa a célula vazia.
    static void valorEmRisco(
            Row linha,
            int coluna,
            Optional<BigDecimal> valor,
            Optional<String> motivoDaAusencia,
            CellStyle estiloMonetario,
            CellStyle estiloDeTexto) {

        if (valor.isPresent()) {
            Cell celula = linha.createCell(coluna);
            celula.setCellValue(valor.get().doubleValue());
            celula.setCellStyle(estiloMonetario);
            return;
        }
        texto(linha, coluna, motivoDaAusencia.orElse(SEM_REFERENCIA), estiloDeTexto);
    }

    // Método estático que junta as evidências numa célula, uma por linha, na mesma ordem nas três colunas de evidência.
    static String juntar(List<String> valores) {
        return String.join(QUEBRA, valores);
    }

    // Método estático que junta valores opcionais, escrevendo a marca indicada onde falta valor.
    static String juntarOpcionais(List<Optional<String>> valores, String marcaDeAusencia) {
        return juntar(valores.stream().map(valor -> valor.orElse(marcaDeAusencia)).toList());
    }
}
