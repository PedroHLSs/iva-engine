package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A verdade de referência: o que uma pessoa afirmou sobre cada item, regra a
 * regra.
 *
 * <p>É a única entrada do harness que o sistema não produziu. Tudo o mais na
 * medição sai do motor; o gabarito sai de alguém que olhou os documentos. Por
 * isso ele é validado com o mesmo rigor de uma carga de catálogo, e não é
 * "limpo" em silêncio.</p>
 *
 * <h2>A mesma linha duas vezes é recusa, não é última-vence</h2>
 *
 * <p>Duas afirmações sobre o mesmo documento, item e regra ou concordam — e uma
 * delas é ruído — ou discordam, e aí não há verdade de referência a usar.
 * Escolher uma delas por ordem de arquivo faria a métrica depender de como o
 * gabarito foi digitado. A carga falha, exatamente como falha uma vigência
 * sobreposta no catálogo (D003).</p>
 */
public final class Gabarito {

    private final List<LinhaDeGabarito> linhas;
    private final Map<EnderecoDaAvaliacao, LinhaDeGabarito> porEndereco;

    public Gabarito(List<LinhaDeGabarito> linhas) {
        if (linhas == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A lista de linhas do gabarito deve ser vazia quando não há nenhuma, nunca nula.");
        }
        if (linhas.stream().anyMatch(Objects::isNull)) {
            throw new AvaliacaoDeAcuraciaInvalida("A lista de linhas do gabarito não pode conter nulo.");
        }

        Map<EnderecoDaAvaliacao, LinhaDeGabarito> indice = new LinkedHashMap<>();
        for (LinhaDeGabarito linha : linhas) {
            LinhaDeGabarito anterior = indice.putIfAbsent(linha.endereco(), linha);
            if (anterior != null) {
                throw new AvaliacaoDeAcuraciaInvalida(recusaDeDuplicata(anterior, linha));
            }
        }

        this.linhas = List.copyOf(linhas);
        this.porEndereco = Map.copyOf(indice);
    }

    /** As linhas, na ordem em que foram lidas. */
    public List<LinhaDeGabarito> linhas() {
        return linhas;
    }

    /** O rótulo afirmado para este endereço, vazio se o gabarito não fala dele. */
    public Optional<RotuloEsperado> rotuloDe(EnderecoDaAvaliacao endereco) {
        if (endereco == null) {
            throw new AvaliacaoDeAcuraciaInvalida("Não há endereço cujo rótulo consultar.");
        }
        return Optional.ofNullable(porEndereco.get(endereco)).map(LinhaDeGabarito::rotulo);
    }

    /** Indica se o gabarito afirma algo sobre este endereço. */
    public boolean contem(EnderecoDaAvaliacao endereco) {
        return rotuloDe(endereco).isPresent();
    }

    /**
     * Identificadores de regra citados pelo gabarito, na ordem em que aparecem.
     *
     * <p>Não é {@code Set.copyOf}: aquele descarta a ordem, e a ordem é o que
     * torna a mensagem de recusa de regra desconhecida legível para quem vai
     * procurar o erro de digitação no arquivo.</p>
     */
    public Set<String> regrasCitadas() {
        Set<String> regras = new LinkedHashSet<>();
        linhas.forEach(linha -> regras.add(linha.regraId()));
        return Collections.unmodifiableSet(regras);
    }

    /** Quantas afirmações o gabarito traz. */
    public int quantidadeDeLinhas() {
        return linhas.size();
    }

    /** Indica se o gabarito não afirma nada. */
    public boolean vazio() {
        return linhas.isEmpty();
    }

    private static String recusaDeDuplicata(LinhaDeGabarito anterior, LinhaDeGabarito repetida) {
        String concordancia = anterior.rotulo() == repetida.rotulo()
                ? "As duas dizem %s, mas ainda assim uma delas sobra e contaria duas vezes."
                        .formatted(anterior.rotulo())
                : "Uma diz %s e a outra diz %s, e não há como saber qual é a verdade de referência."
                        .formatted(anterior.rotulo(), repetida.rotulo());

        return ("O gabarito rotula duas vezes o item %d do documento %s na regra %s, nas linhas %d e %d. "
                + "%s")
                .formatted(
                        repetida.endereco().numeroItem(),
                        repetida.endereco().chaveAcesso().valor(),
                        repetida.regraId(),
                        anterior.numeroDaLinha(),
                        repetida.numeroDaLinha(),
                        concordancia);
    }
}
