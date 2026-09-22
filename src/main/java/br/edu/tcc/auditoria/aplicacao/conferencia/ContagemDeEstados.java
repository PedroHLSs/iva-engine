package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Quantos há de cada um dos quatro estados — sempre os quatro, inclusive os
 * zeros.
 *
 * <p>É a peça que aparece no resumo da nota, no resumo do lote e dentro de cada
 * produto. Em todos esses lugares vale a mesma regra: uma nota com seis
 * produtos sem divergência e quatro não concluídos não pode parecer uma nota
 * quase limpa.</p>
 *
 * <h2>Não existe um estado omitido</h2>
 *
 * <p>O construtor <strong>recusa</strong> mapa a que falte qualquer um dos
 * quatro, em vez de completá-lo com zero. Completar em silêncio faria o defeito
 * de quem montou a contagem virar um zero indistinguível de contagem
 * verdadeira; recusar faz o defeito aparecer no lugar onde ele foi cometido. Um
 * estado que não ocorreu se declara com {@code 0}, e a diferença entre "não
 * ocorreu" e "ninguém contou" é o assunto inteiro deste projeto.</p>
 *
 * <h2>Não há soma de dois estados, e a ausência é deliberada</h2>
 *
 * <p>Este tipo não oferece nenhum método que combine
 * {@link EstadoDeConferencia#SEM_DIVERGENCIA_IDENTIFICADA} com
 * {@link EstadoDeConferencia#NAO_FOI_POSSIVEL_CONCLUIR}, e não deve passar a
 * oferecer. Quem precisar de um total tem {@link #total()}, que soma os quatro e
 * se chama pelo que é.</p>
 */
public record ContagemDeEstados(Map<EstadoDeConferencia, Integer> porEstado) {

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

    /** Contagem dos estados dados, com zero escrito nos que não apareceram. */
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

    /** Os quatro em zero. Nenhum produto contado — que também é uma afirmação. */
    public static ContagemDeEstados nenhum() {
        return new ContagemDeEstados(zerada());
    }

    public int quantidadeDe(EstadoDeConferencia estado) {
        if (estado == null) {
            throw new ConferenciaInvalida("Não há estado cuja quantidade consultar.");
        }
        return porEstado.get(estado);
    }

    /** A soma dos quatro. O único total que este tipo calcula. */
    public int total() {
        return porEstado.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Soma duas contagens estado a estado, para consolidar nota em lote. */
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

    private static Map<EstadoDeConferencia, Integer> zerada() {
        Map<EstadoDeConferencia, Integer> contagem = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            contagem.put(estado, 0);
        }
        return contagem;
    }
}
