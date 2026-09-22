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

/**
 * Uma linha de dados de um CSV, com acesso por nome de coluna.
 *
 * <p>Guarda o número da linha física no arquivo — contando comentários e linhas
 * em branco — para que toda recusa possa apontar onde corrigir.</p>
 *
 * <p>A distinção entre coluna ausente e valor vazio é intencional e vale as duas
 * como erros diferentes: coluna que não existe no cabeçalho é arquivo errado,
 * e falha sempre; valor em branco é ausência de dado, e vira
 * {@code Optional.empty()} — quem exigir o dado chama a variante obrigatória.</p>
 *
 * @param numero   linha física no arquivo, começando em 1
 * @param valores  valores por nome de coluna, já sem aspas
 * @param recusa   como nomear a falha quando o valor lido não servir; vem de
 *                 quem abriu o arquivo, porque só ele sabe de que assunto ele é
 */
public record LinhaCsv(int numero, Map<String, String> valores, RecusaDeCsv recusa) {

    private static final String SEPARADOR_DE_LISTA = "|";

    /*
     * Estrito de propósito: no modo padrão do java.time, "31/02/1900" viraria
     * 28/02/1900 sem aviso, e uma vigência errada resolve o documento contra o
     * registro errado. Ano com quatro dígitos, dia e mês com dois.
     */
    private static final DateTimeFormatter DIA_MES_ANO =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public LinhaCsv {
        if (recusa == null) {
            throw new IllegalArgumentException(
                    "A linha de CSV precisa saber como recusar um valor que não serve.");
        }
        valores = Map.copyOf(valores);
    }

    /** Valor da coluna, vazio quando o campo veio em branco. */
    public Optional<String> texto(String coluna) {
        if (!valores.containsKey(coluna)) {
            throw recusa.de(
                    "Linha %d: o arquivo não tem a coluna \"%s\". Colunas encontradas: %s."
                            .formatted(numero, coluna, valores.keySet()));
        }
        String valor = valores.get(coluna);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }

    /** Valor da coluna, recusando a linha quando o campo veio em branco. */
    public String textoObrigatorio(String coluna) {
        return texto(coluna).orElseThrow(() -> recusa.de(
                "Linha %d: a coluna \"%s\" é obrigatória e veio em branco.".formatted(numero, coluna)));
    }

    public Optional<LocalDate> data(String coluna) {
        return texto(coluna).map(valor -> converterData(coluna, valor));
    }

    public LocalDate dataObrigatoria(String coluna) {
        return converterData(coluna, textoObrigatorio(coluna));
    }

    /** Decimal aceitando vírgula ou ponto como separador. */
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

    /** Inteiro da coluna, recusando a linha quando o campo veio em branco ou não for número. */
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

    /** Lista de valores separados por {@code |} dentro de um único campo; vazia quando o campo veio em branco. */
    public List<String> lista(String coluna) {
        return texto(coluna)
                .map(valor -> Arrays.stream(valor.split("\\" + SEPARADOR_DE_LISTA))
                        .map(String::strip)
                        .filter(item -> !item.isBlank())
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Converte usando o domínio, acrescentando o número da linha se o domínio recusar.
     *
     * <p>Sem isto, um NCM malformado na linha 40 produziria "O NCM deve ter 8
     * dígitos" sem dizer onde.</p>
     */
    public <T> T converterCom(Supplier<T> conversao) {
        try {
            return conversao.get();
        } catch (ExcecaoDeDominio recusaDoDominio) {
            throw recusa.de(
                    "Linha %d: %s".formatted(numero, recusaDoDominio.getMessage()), recusaDoDominio);
        }
    }

    /*
     * Emenda de 14/09/2026, sobre as Etapas 2 e 7.
     *
     * Até esta data só aceitava aaaa-mm-dd, e a mensagem dizia "deve estar no
     * formato aaaa-mm-dd". Passou a aceitar também dd/mm/aaaa, que é como a
     * planilha brasileira grava a data ao salvar em CSV. A barra escolhe o
     * formato, e não uma tentativa em sequência: assim a recusa vem do formato
     * que o arquivo de fato usou.
     *
     * O risco aceito, e registrado na D012: um arquivo em mm/dd/aaaa seria lido
     * trocado sem aviso sempre que o dia fosse até 12.
     */
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
