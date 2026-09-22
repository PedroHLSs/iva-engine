package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Leitura dos parâmetros de consulta, com as recusas num lugar só.
 *
 * <p>Texto em branco não vira filtro vazio: vira recusa. {@code ?regra=} é mais
 * provavelmente um cliente montando URL errado do que alguém pedindo "todas as
 * regras", e tratar os dois como a mesma coisa faria um filtro quebrado passar por
 * consulta sem filtro. É a mesma razão pela qual {@code FiltroDeAchados} recusa
 * regra em branco em vez de normalizá-la para {@code Optional.empty()} (D002).</p>
 */
final class Parametros {

    private Parametros() {
    }

    static Optional<String> textoOpcional(String valor, String nome) {
        if (valor == null) {
            return Optional.empty();
        }
        if (valor.isBlank()) {
            throw new PedidoInvalido(
                    ("O parâmetro \"%s\" veio vazio. Para não filtrar por ele, omita-o da URL em vez "
                            + "de mandá-lo em branco.").formatted(nome));
        }
        return Optional.of(valor.strip());
    }

    /*
     * Acrescentado na Etapa 11, sobre a Etapa 8.
     *
     * A data é obrigatória na consulta à base tributária, e é obrigatória por
     * decisão: a D003 admitiu um caso de uso com data explícita, e "sem data" não
     * pode cair silenciosamente em hoje. Por isso a ausência é recusa, e não
     * valor padrão.
     */
    static LocalDate dataObrigatoria(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new PedidoInvalido(
                    ("O parâmetro \"%s\" é obrigatório, no formato aaaa-mm-dd. A base normativa muda "
                            + "ao longo do tempo, e uma resposta sem data não diz a que dia se "
                            + "refere.").formatted(nome));
        }
        try {
            return LocalDate.parse(valor.strip());
        } catch (DateTimeParseException naoEhData) {
            throw new PedidoInvalido(
                    ("O parâmetro \"%s\" precisa estar no formato aaaa-mm-dd.").formatted(nome));
        }
    }

    /*
     * Acrescentado na Etapa 11, sobre a Etapa 8.
     *
     * O nome da constante vem na URL e a recusa lista as que existem: quem
     * montou o pedido errado descobre o certo sem ter de abrir documentação.
     */
    static <E extends Enum<E>> E constante(String valor, Class<E> tipo, String nome, E padrao) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        for (E candidata : tipo.getEnumConstants()) {
            if (candidata.name().equalsIgnoreCase(valor.strip())) {
                return candidata;
            }
        }
        throw new PedidoInvalido(
                ("O parâmetro \"%s\" não aceita \"%s\". Os valores possíveis são %s.")
                        .formatted(nome, valor, java.util.Arrays.toString(tipo.getEnumConstants())));
    }

    static int pagina(int pagina) {
        if (pagina < 0) {
            throw new PedidoInvalido(
                    "A página não pode ser negativa, mas veio %d. A primeira é 0.".formatted(pagina));
        }
        return pagina;
    }

    static int tamanho(int tamanho) {
        if (tamanho < 1) {
            throw new PedidoInvalido(
                    "O tamanho da página deve ser maior ou igual a 1, mas veio %d.".formatted(tamanho));
        }
        if (tamanho > PaginaExposta.TAMANHO_MAXIMO) {
            throw new PedidoInvalido(
                    ("O tamanho máximo de página é %d, mas veio %d. O limite existe porque uma "
                            + "execução real produz apontamento demais para uma resposta só.")
                            .formatted(PaginaExposta.TAMANHO_MAXIMO, tamanho));
        }
        return tamanho;
    }
}
