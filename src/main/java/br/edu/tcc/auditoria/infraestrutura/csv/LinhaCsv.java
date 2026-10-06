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

// Representa uma linha de dados do CSV, com acesso pelo nome da coluna e o número da linha no arquivo, para o erro dizer onde corrigir. Coluna que não existe no cabeçalho é erro sempre; valor em branco vira Optional vazio. Emenda da Etapa 12: quando quem chama implementa RecusaPorCampo, a recusa leva também a coluna e o valor, para a importação listar todas as linhas de uma vez; as mensagens são as mesmas de antes.
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
            throw recusarColunaAusente(coluna,
                    "Linha %d: o arquivo não tem a coluna \"%s\". Colunas encontradas: %s."
                            .formatted(numero, coluna, valores.keySet()));
        }
        String valor = valores.get(coluna);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }

    // Devolve o valor da coluna; recusa a linha quando veio em branco.
    public String textoObrigatorio(String coluna) {
        return texto(coluna).orElseThrow(() -> recusarCampo(coluna,
                "Linha %d: a coluna \"%s\" é obrigatória e veio em branco.".formatted(numero, coluna),
                null));
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
                throw recusarCampo(coluna,
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
            throw recusarCampo(coluna,
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
        throw recusarCampo(coluna,
                "Linha %d: a coluna \"%s\" aceita apenas \"true\" ou \"false\", mas veio \"%s\"."
                        .formatted(numero, coluna, valor),
                null);
    }

    // Devolve a lista de valores separados por | dentro do campo, ou lista vazia quando veio em branco.
    // Emenda de 04/10/2026 (D025): elemento vazio — "AAA||BBB", "AAA|", "|AAA" — recusa a linha, com a coluna e o valor. Até essa data era descartado em silêncio, e a lista ficava menor do que a pessoa escreveu. Espaço em volta de cada elemento continua sendo tirado, e célula em branco continua sendo lista vazia.
    public List<String> lista(String coluna) {
        Optional<String> valor = texto(coluna);
        if (valor.isEmpty()) {
            return List.of();
        }
        List<String> itens = Arrays.stream(valor.get().split("\\" + SEPARADOR_DE_LISTA, -1))
                .map(String::strip)
                .toList();
        if (itens.stream().anyMatch(String::isEmpty)) {
            throw recusarCampo(coluna,
                    ("Linha %d: a coluna \"%s\" tem elemento vazio na lista: \"%s\". Separe os valores por um "
                            + "%s só, sem %s sobrando no começo, no fim ou dois seguidos.")
                            .formatted(numero, coluna, valor.get(), SEPARADOR_DE_LISTA, SEPARADOR_DE_LISTA),
                    null);
        }
        return itens;
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

    // Converte usando o domínio e, se o domínio recusar, diz a linha, a coluna e o valor que ele recusou. Acrescentado na Etapa 12.
    public <T> T converterCom(String coluna, Supplier<T> conversao) {
        try {
            return conversao.get();
        } catch (ExcecaoDeDominio recusaDoDominio) {
            throw recusarCampo(coluna,
                    "Linha %d: %s".formatted(numero, recusaDoDominio.getMessage()), recusaDoDominio);
        }
    }

    // Devolve o valor da coluna como veio no arquivo, sem cortar espaço, ou texto vazio se a coluna não existe. Serve só para a mensagem de erro.
    public String valorComoVeio(String coluna) {
        String valor = valores.get(coluna);
        return valor == null ? "" : valor;
    }

    // Método auxiliar que monta a recusa de um valor, com coluna e valor quando quem chama sabe recebê-los.
    private RuntimeException recusarCampo(String coluna, String mensagem, Throwable causa) {
        if (recusa instanceof RecusaPorCampo porCampo) {
            return porCampo.deCampo(numero, coluna, valorComoVeio(coluna), mensagem, causa);
        }
        return recusa.de(mensagem, causa);
    }

    // Método auxiliar que monta a recusa de coluna ausente, que é problema do arquivo inteiro e não só desta linha.
    private RuntimeException recusarColunaAusente(String coluna, String mensagem) {
        if (recusa instanceof RecusaPorCampo porCampo) {
            return porCampo.deColunaAusente(numero, coluna, mensagem);
        }
        return recusa.de(mensagem);
    }

    // Método auxiliar que lê a data em aaaa-mm-dd ou dd/mm/aaaa, escolhendo pela barra. Aceita dd/mm/aaaa desde 14/09/2026; risco registrado na D012: arquivo em mm/dd/aaaa seria lido trocado quando o dia for até 12.
    private LocalDate converterData(String coluna, String valor) {
        try {
            return valor.contains("/") ? LocalDate.parse(valor, DIA_MES_ANO) : LocalDate.parse(valor);
        } catch (DateTimeParseException formatoInvalido) {
            throw recusarCampo(coluna,
                    "Linha %d: a coluna \"%s\" deve estar no formato aaaa-mm-dd ou dd/mm/aaaa, mas veio \"%s\"."
                            .formatted(numero, coluna, valor),
                    formatoInvalido);
        }
    }
}
