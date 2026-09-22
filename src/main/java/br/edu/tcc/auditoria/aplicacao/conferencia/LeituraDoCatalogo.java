package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O que o catálogo respondeu sobre um ponto, <strong>ou por que não
 * respondeu</strong>.
 *
 * <h2>Não existe bloco vazio e mudo</h2>
 *
 * <p>Este tipo tem uma regra só, e é a razão de ele existir: lista vazia obriga
 * motivo, e lista com conteúdo proíbe motivo. As duas metades são recusadas no
 * construtor, de modo que a tela não tem como exibir um quadro em branco nem um
 * quadro cheio com uma desculpa ao lado.</p>
 *
 * <p>É a mesma disciplina de {@code ValorEmRisco} e de {@code VersaoDaRegra},
 * aplicada ao caso em que a resposta é uma lista. Aqui ela importa mais que nos
 * outros dois, porque é justamente sobre tratamento tributário que o silêncio da
 * interface seria lido como {@code "nada a declarar"} — quando o que houve foi o
 * catálogo não alcançar a pergunta.</p>
 *
 * <p>A lista vem ordenada por quem monta e é copiada aqui. Ordem estável importa:
 * duas aberturas da mesma tela precisam mostrar as mesmas linhas na mesma
 * sequência para que a conferência seja conferível.</p>
 *
 * @param encontrado      o que a carga trouxe, possivelmente vazio
 * @param motivoDaAusencia por que não trouxe, obrigatório quando vazio
 */
public record LeituraDoCatalogo<T>(List<T> encontrado, Optional<String> motivoDaAusencia) {

    public LeituraDoCatalogo {
        if (encontrado == null) {
            throw new ConferenciaInvalida(
                    "A leitura do catálogo precisa de lista, vazia quando nada foi encontrado.");
        }
        if (motivoDaAusencia == null) {
            throw new ConferenciaInvalida(
                    "Ausência de motivo se representa com Optional.empty(), nunca com nulo.");
        }
        if (encontrado.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A leitura do catálogo não pode conter elemento nulo.");
        }
        if (encontrado.isEmpty() && motivoDaAusencia.isEmpty()) {
            throw new ConferenciaInvalida(
                    "O catálogo não respondeu e não foi dito por quê. Quadro em branco na tela de "
                            + "tratamento é lido como ausência de tratamento, que é outra coisa.");
        }
        if (!encontrado.isEmpty() && motivoDaAusencia.isPresent()) {
            throw new ConferenciaInvalida(
                    "Há conteúdo do catálogo e, ao mesmo tempo, um motivo para não haver. As duas "
                            + "afirmações não podem valer juntas.");
        }
        if (motivoDaAusencia.isPresent() && motivoDaAusencia.get().isBlank()) {
            throw new ConferenciaInvalida("O motivo da ausência não pode ser texto em branco.");
        }
        encontrado = List.copyOf(encontrado);
    }

    /** O catálogo respondeu isto. Recusa lista vazia: para isso existe {@link #ausente}. */
    public static <T> LeituraDoCatalogo<T> de(List<T> encontrado) {
        return new LeituraDoCatalogo<>(encontrado, Optional.empty());
    }

    /** O catálogo não respondeu, e este é o motivo. */
    public static <T> LeituraDoCatalogo<T> ausente(String motivo) {
        return new LeituraDoCatalogo<>(List.of(), Optional.of(motivo == null ? "" : motivo));
    }

    /** Uma resposta única, quando o catálogo devolve no máximo um registro. */
    public static <T> LeituraDoCatalogo<T> deUnico(Optional<T> encontrado, String motivoSeVazio) {
        return encontrado
                .map(valor -> LeituraDoCatalogo.de(List.of(valor)))
                .orElseGet(() -> LeituraDoCatalogo.ausente(motivoSeVazio));
    }

    public boolean respondido() {
        return !encontrado.isEmpty();
    }

    public int quantidade() {
        return encontrado.size();
    }
}
