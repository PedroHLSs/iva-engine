package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa o que o catálogo respondeu sobre um ponto, ou por que não respondeu; lista vazia exige motivo.
public record LeituraDoCatalogo<T>(List<T> encontrado, Optional<String> motivoDaAusencia) {

    // Valida que lista vazia venha com motivo e lista com conteúdo venha sem motivo.
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

    // Método estático para quando o catálogo respondeu; lista vazia deve usar ausente.
    public static <T> LeituraDoCatalogo<T> de(List<T> encontrado) {
        return new LeituraDoCatalogo<>(encontrado, Optional.empty());
    }

    // Método estático para quando o catálogo não respondeu, com o motivo.
    public static <T> LeituraDoCatalogo<T> ausente(String motivo) {
        return new LeituraDoCatalogo<>(List.of(), Optional.of(motivo == null ? "" : motivo));
    }

    // Método estático para respostas de no máximo um registro.
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
