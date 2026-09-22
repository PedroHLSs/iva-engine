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

//Classe que representa o gabarito da avaliação de acurácia, contendo as linhas do gabarito e métodos para acessar informações sobre ele.
public final class Gabarito {

    private final List<LinhaDeGabarito> linhas;
    private final Map<EnderecoDaAvaliacao, LinhaDeGabarito> porEndereco;

    // Construtor que recebe uma lista de linhas do gabarito e valida se não há duplicatas ou elementos nulos.
    public Gabarito(List<LinhaDeGabarito> linhas) {
        if (linhas == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A lista de linhas do gabarito deve ser vazia quando não há nenhuma, nunca nula.");
        }
        if (linhas.stream().anyMatch(Objects::isNull)) {
            throw new AvaliacaoDeAcuraciaInvalida("A lista de linhas do gabarito não pode conter nulo.");
        }
        // Garante que não haja duplicatas de endereço no gabarito, para evitar contagem errada de acertos e erros.
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

    public List<LinhaDeGabarito> linhas() {
        return linhas;
    }

    // Retorna o rótulo esperado para o endereço da avaliação, se houver.
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

    // Retorna o conjunto de identificadores de regras citadas no gabarito, sem duplicatas e na ordem em que aparecem.
    public Set<String> regrasCitadas() {
        Set<String> regras = new LinkedHashSet<>();
        linhas.forEach(linha -> regras.add(linha.regraId()));
        return Collections.unmodifiableSet(regras);
    }

    public int quantidadeDeLinhas() {
        return linhas.size();
    }

    public boolean vazio() {
        return linhas.isEmpty();
    }
    // Garante que o gabarito não cite regras que não estão no conjunto de regras, para evitar erros de digitação.
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
