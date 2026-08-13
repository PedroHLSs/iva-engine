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

/**
 * As versões sucessivas de um mesmo registro do catálogo, ordenadas no tempo.
 *
 * <p>É aqui que mora a invariante do catálogo: <strong>duas versões da mesma
 * chave não podem valer na mesma data.</strong> A série se recusa a existir se
 * houver sobreposição, e a recusa acontece na construção — antes de qualquer
 * consulta, e portanto antes de qualquer relatório sair errado.</p>
 *
 * <p>A invariante fica no domínio, e não no repositório, de propósito: ela não é
 * detalhe de como o catálogo é guardado. Trocar armazenamento em memória por
 * banco não pode ser oportunidade de perdê-la.</p>
 *
 * <p>Consequência de haver a invariante: {@link #vigenteEm(LocalDate)} devolve
 * no máximo um registro, e devolvê-lo não envolve escolha nenhuma. Se houvesse
 * sobreposição, alguém teria de decidir qual das duas versões responder — e
 * qualquer critério para isso seria inventado.</p>
 *
 * @param chave   identificação da série, comum a todas as versões
 * @param versoes versões ordenadas por início de vigência, ao menos uma
 */
public record SerieNormativa<T extends RegistroNormativo>(String chave, List<T> versoes) {

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

        // Basta comparar vizinhas: numa lista ordenada por início de vigência,
        // se duas quaisquer se sobrepõem, então duas adjacentes se sobrepõem.
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

    /** Monta a série a partir das versões, tomando a chave da primeira delas. */
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

    /**
     * A versão que vale na data, se alguma valer.
     *
     * <p>Data anterior à primeira vigência, posterior à última, ou caída num
     * intervalo descoberto entre duas versões devolvem vazio. Vazio significa
     * "o catálogo não diz nada sobre isso nesta data", e é a resposta correta —
     * não um erro a ser contornado devolvendo a versão mais próxima.</p>
     */
    public Optional<T> vigenteEm(LocalDate data) {
        if (data == null) {
            throw new CatalogoInvalido("A data de consulta ao catálogo não pode ser nula.");
        }
        return versoes.stream().filter(versao -> versao.vigenteEm(data)).findFirst();
    }

    /** Quantidade de versões da série. */
    public int quantidadeDeVersoes() {
        return versoes.size();
    }

    private static boolean seSobrepoem(PeriodoVigencia uma, PeriodoVigencia outra) {
        return comecaAteOFimDe(uma, outra) && comecaAteOFimDe(outra, uma);
    }

    /** Verdadeiro se o início de {@code inicial} não passa do fim de {@code outra}; vigência aberta não tem fim. */
    private static boolean comecaAteOFimDe(PeriodoVigencia inicial, PeriodoVigencia outra) {
        return outra.fim().map(ultimoDia -> !inicial.inicio().isAfter(ultimoDia)).orElse(true);
    }

    private static String descreverFim(Optional<LocalDate> fim) {
        return fim.map(LocalDate::toString).orElse("sem fim declarado");
    }
}
