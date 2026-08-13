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

/**
 * Armazenamento em memória de um tipo de registro do catálogo, agrupado em
 * séries por chave de vigência.
 *
 * <p>A validação de sobreposição não está aqui: ela acontece dentro de
 * {@link SerieNormativa}, no domínio. Esta classe só agrupa e delega — quando o
 * armazenamento virar banco, a invariante continua onde está.</p>
 *
 * <p>A carga é feita inteira no construtor, e falha inteira se qualquer série
 * for contraditória. Catálogo meio carregado responderia vazio para o que
 * faltou, e vazio significa "o catálogo nada diz" — o relatório sairia com
 * "não avaliado" onde na verdade houve erro de carga.</p>
 */
final class CatalogoEmMemoria<T extends RegistroNormativo> {

    private final Map<String, SerieNormativa<T>> seriesPorChave;

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

    /** O registro da chave que valia na data, se algum valia. */
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

    /** Todos os registros, de todas as chaves, que valiam na data. */
    List<T> vigentesEm(LocalDate data) {
        if (data == null) {
            return List.of();
        }
        return seriesPorChave.values().stream()
                .map(serie -> serie.vigenteEm(data))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Quantidade de séries carregadas. */
    int quantidadeDeSeries() {
        return seriesPorChave.size();
    }
}
