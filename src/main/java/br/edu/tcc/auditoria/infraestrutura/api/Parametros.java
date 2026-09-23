package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

// Classe que lê os parâmetros da URL e junta as recusas num lugar só. Parâmetro em branco é recusado, e não vira "sem filtro", porque quase sempre é URL montada errado.
final class Parametros {

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private Parametros() {
    }

    // Método estático que lê um texto opcional: ausente vira vazio, e em branco é recusado.
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

    // Método estático que lê a data obrigatória no formato aaaa-mm-dd. Sem data é recusado, e não vira "hoje", porque a base normativa muda com o tempo.
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

    // Método estático que converte o texto numa constante do enum, sem ligar para maiúscula ou minúscula; ausente ou em branco usa o padrão, e valor desconhecido é recusado com a lista dos aceitos.
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

    // Método estático que confere o número da página; a primeira é 0, e número negativo é recusado.
    static int pagina(int pagina) {
        if (pagina < 0) {
            throw new PedidoInvalido(
                    "A página não pode ser negativa, mas veio %d. A primeira é 0.".formatted(pagina));
        }
        return pagina;
    }

    // Método estático que confere o tamanho da página, que vai de 1 até o máximo.
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
