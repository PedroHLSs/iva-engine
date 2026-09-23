package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa as versões sucessivas de um mesmo registro do catálogo, ordenadas no tempo; recusa na construção duas versões valendo na mesma data.
public record SerieNormativa<T extends RegistroNormativo>(String chave, List<T> versoes) {

    // Valida a série: exige versões da mesma chave, ordena por início de vigência e recusa vigências sobrepostas.
    public SerieNormativa {
        if (chave == null || chave.isBlank()) {
            throw new CatalogoInvalido("Uma série normativa precisa de chave.");
        }
        if (versoes == null) {
            throw new CatalogoInvalido("A lista de versões da série \"%s\" não pode ser nula.".formatted(chave));
        }
        if (versoes.isEmpty()) {
            throw new CatalogoInvalido("A série \"%s\" precisa de ao menos uma versão.".formatted(chave));
        }
        if (versoes.stream().anyMatch(Objects::isNull)) {
            throw new CatalogoInvalido("A série \"%s\" não pode conter versão nula.".formatted(chave));
        }

        for (T versao : versoes) {
            if (!chave.equals(versao.chaveDeVigencia())) {
                throw new CatalogoInvalido(
                        "A série \"%s\" recebeu uma versão de outra chave: \"%s\"."
                                .formatted(chave, versao.chaveDeVigencia()));
            }
        }

        List<T> ordenadas = versoes.stream()
                .sorted(Comparator.comparing((T versao) -> versao.vigenciaInicio()))
                .toList();

        // Basta comparar vizinhas: numa lista ordenada por início, se duas quaisquer se sobrepõem, duas adjacentes também se sobrepõem.
        for (int posicao = 1; posicao < ordenadas.size(); posicao++) {
            T anterior = ordenadas.get(posicao - 1);
            T seguinte = ordenadas.get(posicao);
            if (seSobrepoem(anterior.vigencia(), seguinte.vigencia())) {
                throw new CatalogoInvalido(
                        ("Vigências sobrepostas na chave \"%s\": uma versão vale de %s a %s e outra de %s a %s. "
                                + "Duas versões do mesmo registro valendo na mesma data tornam a consulta "
                                + "ambígua; corrija o catálogo em vez de escolher uma delas.")
                                .formatted(
                                        chave,
                                        anterior.vigenciaInicio(),
                                        descreverFim(anterior.vigenciaFim()),
                                        seguinte.vigenciaInicio(),
                                        descreverFim(seguinte.vigenciaFim())));
            }
        }

        versoes = ordenadas;
    }

    // Método estático que monta a série a partir das versões, tomando a chave da primeira.
    public static <T extends RegistroNormativo> SerieNormativa<T> de(Collection<T> versoes) {
        if (versoes == null || versoes.isEmpty()) {
            throw new CatalogoInvalido("Uma série normativa precisa de ao menos uma versão.");
        }
        T primeira = versoes.iterator().next();
        if (primeira == null) {
            throw new CatalogoInvalido("Uma série normativa não pode conter versão nula.");
        }
        return new SerieNormativa<>(primeira.chaveDeVigencia(), new ArrayList<>(versoes));
    }

    // Retorna a versão que vale na data; vazio quer dizer que o catálogo nada diz, e nunca se devolve a versão mais próxima.
    public Optional<T> vigenteEm(LocalDate data) {
        if (data == null) {
            throw new CatalogoInvalido("A data de consulta ao catálogo não pode ser nula.");
        }
        return versoes.stream().filter(versao -> versao.vigenteEm(data)).findFirst();
    }

    public int quantidadeDeVersoes() {
        return versoes.size();
    }

    // Método auxiliar que indica se dois períodos de vigência se sobrepõem.
    private static boolean seSobrepoem(PeriodoVigencia uma, PeriodoVigencia outra) {
        return comecaAteOFimDe(uma, outra) && comecaAteOFimDe(outra, uma);
    }

    // Método auxiliar que indica se um período começa até o fim do outro; vigência aberta não tem fim.
    private static boolean comecaAteOFimDe(PeriodoVigencia inicial, PeriodoVigencia outra) {
        return outra.fim().map(ultimoDia -> !inicial.inicio().isAfter(ultimoDia)).orElse(true);
    }

    // Método auxiliar que escreve o fim da vigência, ou "sem fim declarado", para a mensagem de erro.
    private static String descreverFim(Optional<LocalDate> fim) {
        return fim.map(LocalDate::toString).orElse("sem fim declarado");
    }
}
