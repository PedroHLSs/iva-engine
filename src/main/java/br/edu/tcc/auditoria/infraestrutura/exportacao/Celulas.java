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

/**
 * Escrita de células, com uma decisão só e importante: <strong>ausência é
 * escrita, não deixada em branco.</strong>
 *
 * <p>Numa planilha lida meses depois, célula vazia é indistinguível de célula que
 * ninguém preencheu. O sistema inteiro é construído sobre a diferença entre "o
 * campo não veio no documento" e "o campo veio com zero", e essa diferença não
 * pode se perder na última etapa. Por isso ausência vira um texto explícito, e
 * zero vira o número zero.</p>
 */
final class Celulas {

    /** O que aparece onde o documento não declarou o campo. */
    static final String NAO_INFORMADO = "(não informado)";

    /** O que aparece onde a regra não tinha valor de referência a opor. */
    static final String SEM_REFERENCIA = "(sem referência)";

    /** O que aparece onde a vigência não tem fim declarado. */
    static final String SEM_FIM_DECLARADO = "(sem fim declarado)";

    /** Separador de evidências dentro de uma célula. */
    private static final String QUEBRA = "\n";

    private final ZoneId fusoDeApresentacao;

    Celulas(ZoneId fusoDeApresentacao) {
        this.fusoDeApresentacao = fusoDeApresentacao;
    }

    static void texto(Row linha, int coluna, String valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    static void inteiro(Row linha, int coluna, long valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    static void data(Row linha, int coluna, LocalDate valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    void dataHora(Row linha, int coluna, Instant valor, CellStyle estilo) {
        Cell celula = linha.createCell(coluna);
        celula.setCellValue(LocalDateTime.ofInstant(valor, fusoDeApresentacao));
        celula.setCellStyle(estilo);
    }

    /**
     * Escreve o valor em risco como número, ou o motivo de não haver como texto.
     *
     * <p>Nunca deixa a célula vazia: sem montante e sem motivo, quem lê não sabe
     * se o apontamento não tem valor aferível ou se o sistema deixou de calcular.</p>
     */
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

    /**
     * Junta as evidências numa célula, uma por linha.
     *
     * <p>As três colunas de evidência — campo, encontrado e esperado — são
     * escritas com a mesma quantidade de linhas e na mesma ordem, de modo que a
     * enésima linha de uma corresponde à enésima linha das outras.</p>
     */
    static String juntar(List<String> valores) {
        return String.join(QUEBRA, valores);
    }

    /** Junta valores opcionais, escrevendo a marca indicada onde não há valor. */
    static String juntarOpcionais(List<Optional<String>> valores, String marcaDeAusencia) {
        return juntar(valores.stream().map(valor -> valor.orElse(marcaDeAusencia)).toList());
    }
}
