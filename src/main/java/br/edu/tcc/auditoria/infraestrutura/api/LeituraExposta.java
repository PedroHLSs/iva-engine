package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.LeituraDoCatalogo;

import java.util.List;
import java.util.function.Function;

/**
 * O que a base normativa respondeu sobre um ponto, ou por que não respondeu.
 *
 * <p>É a forma da {@code LeituraDoCatalogo} da aplicação na borda HTTP, com a
 * mesma regra levada até o construtor: lista vazia obriga motivo, lista com
 * conteúdo proíbe motivo. Sem isso, um bloco vazio chegaria à tela como espaço em
 * branco, e espaço em branco numa tela de tratamento tributário é lido como
 * "nada a declarar" — que é outra afirmação, e ninguém a fez.</p>
 *
 * <p>É o mesmo par da D009, aplicado a uma lista em vez de a um campo.</p>
 */
public record LeituraExposta<T>(List<T> encontrado, String motivoDaAusencia) {

    public LeituraExposta {
        if (encontrado == null) {
            throw new RespostaInvalida(
                    "A leitura da base precisa de lista, vazia quando nada foi encontrado.");
        }
        if (encontrado.isEmpty() && (motivoDaAusencia == null || motivoDaAusencia.isBlank())) {
            throw new RespostaInvalida(
                    "A base normativa não respondeu e não foi dito por quê. Bloco vazio e mudo é lido "
                            + "como ausência de tratamento, que é outra coisa.");
        }
        if (!encontrado.isEmpty() && motivoDaAusencia != null) {
            throw new RespostaInvalida(
                    "Há conteúdo da base e, ao mesmo tempo, um motivo para não haver.");
        }
        encontrado = List.copyOf(encontrado);
    }

    /** Traduz a leitura da aplicação, item a item, preservando a ordem e o motivo. */
    static <A, B> LeituraExposta<B> de(LeituraDoCatalogo<A> leitura, Function<A, B> comoExpor) {
        return new LeituraExposta<>(
                leitura.encontrado().stream().map(comoExpor).toList(),
                leitura.motivoDaAusencia().orElse(null));
    }
}
