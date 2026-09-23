package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

// Representa quantos produtos ou verificações há em cada um dos quatro estados, sempre os quatro, inclusive os zeros.
public record ContagemDeEstados(Map<EstadoDeConferencia, Integer> porEstado) {

    // Valida que a contagem traga os quatro estados, sem valor negativo e sem chave a mais.
    public ContagemDeEstados {
        if (porEstado == null) {
            throw new ConferenciaInvalida(
                    "A contagem por estado deve trazer zero onde o estado não ocorreu, nunca ser nula.");
        }
        Map<EstadoDeConferencia, Integer> copia = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            Integer quantidade = porEstado.get(estado);
            if (quantidade == null) {
                throw new ConferenciaInvalida(
                        ("A contagem não traz o estado \"%s\". Estado que não ocorreu se declara com 0: "
                                + "omiti-lo faria quem lê não saber se ele não ocorreu ou se ninguém o "
                                + "contou.").formatted(estado.rotulo()));
            }
            if (quantidade < 0) {
                throw new ConferenciaInvalida(
                        "A contagem do estado \"%s\" veio negativa: %d.".formatted(
                                estado.rotulo(), quantidade));
            }
            copia.put(estado, quantidade);
        }
        if (porEstado.size() != EstadoDeConferencia.values().length) {
            throw new ConferenciaInvalida(
                    ("A contagem tem %d entradas e os estados são %d. Sobra chave que não é estado de "
                            + "conferência.").formatted(
                            porEstado.size(), EstadoDeConferencia.values().length));
        }
        porEstado = Collections.unmodifiableMap(copia);
    }

    // Método estático que conta os estados dados, escrevendo zero nos que não apareceram.
    public static ContagemDeEstados de(Collection<EstadoDeConferencia> estados) {
        if (estados == null) {
            throw new ConferenciaInvalida(
                    "A coleção de estados a contar deve ser vazia quando não há nenhum, nunca nula.");
        }
        Map<EstadoDeConferencia, Integer> contagem = zerada();
        for (EstadoDeConferencia estado : estados) {
            if (estado == null) {
                throw new ConferenciaInvalida("A coleção de estados a contar tem elemento nulo.");
            }
            contagem.merge(estado, 1, Integer::sum);
        }
        return new ContagemDeEstados(contagem);
    }

    // Método estático que retorna a contagem com os quatro estados em zero.
    public static ContagemDeEstados nenhum() {
        return new ContagemDeEstados(zerada());
    }

    // Retorna a quantidade do estado indicado.
    public int quantidadeDe(EstadoDeConferencia estado) {
        if (estado == null) {
            throw new ConferenciaInvalida("Não há estado cuja quantidade consultar.");
        }
        return porEstado.get(estado);
    }

    // Retorna a soma dos quatro estados, que é o único total que este tipo calcula.
    public int total() {
        return porEstado.values().stream().mapToInt(Integer::intValue).sum();
    }

    // Soma duas contagens estado a estado, para consolidar as notas no lote.
    public ContagemDeEstados mais(ContagemDeEstados outra) {
        if (outra == null) {
            throw new ConferenciaInvalida("Não há contagem a somar.");
        }
        Map<EstadoDeConferencia, Integer> soma = zerada();
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            soma.put(estado, quantidadeDe(estado) + outra.quantidadeDe(estado));
        }
        return new ContagemDeEstados(soma);
    }

    // Método auxiliar que cria o mapa com os quatro estados em zero.
    private static Map<EstadoDeConferencia, Integer> zerada() {
        Map<EstadoDeConferencia, Integer> contagem = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            contagem.put(estado, 0);
        }
        return contagem;
    }
}
