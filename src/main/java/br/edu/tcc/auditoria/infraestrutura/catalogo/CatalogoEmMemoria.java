package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.RegistroNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.SerieNormativa;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Classe que guarda na memória um tipo de registro do catálogo, agrupado em séries pela chave de vigência. A checagem de datas sobrepostas fica no SerieNormativa do domínio, e a carga falha inteira se alguma série for contraditória.
final class CatalogoEmMemoria<T extends RegistroNormativo> {

    private final Map<String, SerieNormativa<T>> seriesPorChave;

    // Construtor que recebe os registros, recusa coleção ou registro nulo e monta uma série por chave.
    CatalogoEmMemoria(Collection<T> registros) {
        if (registros == null) {
            throw new CatalogoInvalido("A carga do catálogo não pode receber coleção nula.");
        }
        if (registros.stream().anyMatch(Objects::isNull)) {
            throw new CatalogoInvalido("A carga do catálogo não pode receber registro nulo.");
        }

        Map<String, List<T>> agrupados = new LinkedHashMap<>();
        for (T registro : registros) {
            agrupados.computeIfAbsent(registro.chaveDeVigencia(), chave -> new ArrayList<>()).add(registro);
        }

        Map<String, SerieNormativa<T>> series = new LinkedHashMap<>();
        agrupados.forEach((chave, versoes) -> series.put(chave, new SerieNormativa<>(chave, versoes)));
        this.seriesPorChave = Map.copyOf(series);
    }

    // Devolve o registro da chave que valia na data, se algum valia.
    Optional<T> vigenteEm(String chave, LocalDate data) {
        if (chave == null || data == null) {
            return Optional.empty();
        }
        SerieNormativa<T> serie = seriesPorChave.get(chave);
        if (serie == null) {
            return Optional.empty();
        }
        return serie.vigenteEm(data);
    }

    // Devolve todos os registros, de todas as chaves, que valiam na data.
    List<T> vigentesEm(LocalDate data) {
        if (data == null) {
            return List.of();
        }
        return seriesPorChave.values().stream()
                .map(serie -> serie.vigenteEm(data))
                .flatMap(Optional::stream)
                .toList();
    }

    // Retorna quantas séries foram carregadas.
    int quantidadeDeSeries() {
        return seriesPorChave.size();
    }
}
