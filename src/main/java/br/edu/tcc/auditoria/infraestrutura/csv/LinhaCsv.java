package br.edu.tcc.auditoria.infraestrutura.csv;

import br.edu.tcc.auditoria.dominio.excecao.ExcecaoDeDominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

// Representa uma linha de dados do CSV, com acesso pelo nome da coluna e o número da linha no arquivo, para o erro dizer onde corrigir. Coluna que não existe no cabeçalho é erro sempre; valor em branco vira Optional vazio.
public record LinhaCsv(int numero, Map<String, String> valores, RecusaDeCsv recusa) {

    private static final String SEPARADOR_DE_LISTA = "|";

    // Formato dd/mm/aaaa estrito: sem ele, 31/02/1900 viraria 28/02/1900 sem aviso.
    private static final DateTimeFormatter DIA_MES_ANO =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    // Valida que haja a forma de recusar e guarda uma cópia dos valores.
    public LinhaCsv {
        if (recusa == null) {
            throw new IllegalArgumentException(
                    "A linha de CSV precisa saber como recusar um valor que não serve.");
        }
        valores = Map.copyOf(valores);
    }

    // Devolve o valor da coluna, ou vazio quando veio em branco; recusa se a coluna não existe.
    public Optional<String> texto(String coluna) {
        if (!valores.containsKey(coluna)) {
            throw recusa.de(
                    "Linha %d: o arquivo não tem a coluna \"%s\". Colunas encontradas: %s."
                            .formatted(numero, coluna, valores.keySet()));
        }
        String valor = valores.get(coluna);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }

    // Devolve o valor da coluna; recusa a linha quando veio em branco.
    public String textoObrigatorio(String coluna) {
        return texto(coluna).orElseThrow(() -> recusa.de(
                "Linha %d: a coluna \"%s\" é obrigatória e veio em branco.".formatted(numero, coluna)));
    }

    // Devolve a data da coluna, ou vazio quando veio em branco.
    public Optional<LocalDate> data(String coluna) {
        return texto(coluna).map(valor -> converterData(coluna, valor));
    }

    // Devolve a data da coluna; recusa a linha quando veio em branco.
    public LocalDate dataObrigatoria(String coluna) {
        return converterData(coluna, textoObrigatorio(coluna));
    }

    // Devolve o número decimal da coluna, aceitando vírgula ou ponto, ou vazio quando veio em branco.
    public Optional<BigDecimal> decimal(String coluna) {
        return texto(coluna).map(valor -> {
            try {
                return new BigDecimal(valor.replace(",", "."));
            } catch (NumberFormatException naoENumero) {
                throw recusa.de(
                        "Linha %d: a coluna \"%s\" não é um número: \"%s\".".formatted(numero, coluna, valor),
                        naoENumero);
            }
        });
    }

    // Devolve o número inteiro da coluna; recusa a linha quando veio em branco ou não é número.
    public int inteiroObrigatorio(String coluna) {
        String valor = textoObrigatorio(coluna);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException naoENumero) {
            throw recusa.de(
                    "Linha %d: a coluna \"%s\" deve ser um número inteiro, mas veio \"%s\"."
                            .formatted(numero, coluna, valor),
                    naoENumero);
        }
    }

    // Devolve true ou false da coluna; recusa qualquer outro valor.
    public boolean booleanoObrigatorio(String coluna) {
        String valor = textoObrigatorio(coluna);
        if ("true".equalsIgnoreCase(valor)) {
            return true;
        }
        if ("false".equalsIgnoreCase(valor)) {
            return false;
        }
        throw recusa.de(
                "Linha %d: a coluna \"%s\" aceita apenas \"true\" ou \"false\", mas veio \"%s\"."
                        .formatted(numero, coluna, valor));
    }

    // Devolve a lista de valores separados por | dentro do campo, ou lista vazia quando veio em branco.
    public List<String> lista(String coluna) {
        return texto(coluna)
                .map(valor -> Arrays.stream(valor.split("\\" + SEPARADOR_DE_LISTA))
                        .map(String::strip)
                        .filter(item -> !item.isBlank())
                        .toList())
                .orElseGet(List::of);
    }

    // Converte usando o domínio e, se o domínio recusar, põe o número da linha na mensagem.
    public <T> T converterCom(Supplier<T> conversao) {
        try {
            return conversao.get();
        } catch (ExcecaoDeDominio recusaDoDominio) {
            throw recusa.de(
                    "Linha %d: %s".formatted(numero, recusaDoDominio.getMessage()), recusaDoDominio);
        }
    }

    // Método auxiliar que lê a data em aaaa-mm-dd ou dd/mm/aaaa, escolhendo pela barra. Aceita dd/mm/aaaa desde 14/09/2026; risco registrado na D012: arquivo em mm/dd/aaaa seria lido trocado quando o dia for até 12.
    private LocalDate converterData(String coluna, String valor) {
        try {
            return valor.contains("/") ? LocalDate.parse(valor, DIA_MES_ANO) : LocalDate.parse(valor);
        } catch (DateTimeParseException formatoInvalido) {
            throw recusa.de(
                    "Linha %d: a coluna \"%s\" deve estar no formato aaaa-mm-dd ou dd/mm/aaaa, mas veio \"%s\"."
                            .formatted(numero, coluna, valor),
                    formatoInvalido);
        }
    }
}
