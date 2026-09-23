package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.LeituraDoCatalogo;

import java.util.List;
import java.util.function.Function;

// Representa o que a base normativa respondeu sobre um ponto, ou por que não respondeu. Lista vazia exige motivo e lista com conteúdo não aceita motivo, para a tela nunca mostrar um bloco em branco.
public record LeituraExposta<T>(List<T> encontrado, String motivoDaAusencia) {

    // Valida que a lista exista, que lista vazia venha com motivo e que lista com conteúdo venha sem motivo.
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

    // Método estático que converte a leitura da aplicação, item por item, mantendo a ordem e o motivo.
    static <A, B> LeituraExposta<B> de(LeituraDoCatalogo<A> leitura, Function<A, B> comoExpor) {
        return new LeituraExposta<>(
                leitura.encontrado().stream().map(comoExpor).toList(),
                leitura.motivoDaAusencia().orElse(null));
    }
}
