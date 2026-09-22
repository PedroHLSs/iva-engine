package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O que um CSV de catálogo produziu: os registros e a procedência deles.
 *
 * <p>Os dois juntos, e não a lista sozinha, porque a procedência é fato sobre
 * <em>aquelas</em> linhas. Devolvê-la por outro caminho abriria a possibilidade
 * de alguém ler os registros de um arquivo e a natureza de outro — que é
 * precisamente o erro que a marcação existe para impedir.</p>
 *
 * <p>O construtor casa as duas coisas: lista vazia obriga natureza vazia, e lista
 * com registro obriga natureza declarada.</p>
 */
public record TabelaImportada<T>(List<T> registros, Optional<Natureza> natureza) {

    public TabelaImportada {
        if (registros == null) {
            throw new ImportacaoDeCatalogoInvalida(
                    "A lista de registros deve ser vazia quando o arquivo só tem cabeçalho, nunca nula.");
        }
        if (registros.stream().anyMatch(Objects::isNull)) {
            throw new ImportacaoDeCatalogoInvalida("A lista de registros não pode conter nulo.");
        }
        if (natureza == null) {
            throw new ImportacaoDeCatalogoInvalida(
                    "Ausência de natureza se representa com Optional.empty(), nunca com nulo.");
        }
        if (registros.isEmpty() != natureza.isEmpty()) {
            throw new ImportacaoDeCatalogoInvalida(
                    ("O arquivo trouxe %d registro(s) e %s natureza. As duas coisas precisam "
                            + "concordar: registro sem procedência declarada é o que esta coluna "
                            + "existe para impedir, e procedência sem registro é afirmação sobre "
                            + "conteúdo que não existe.")
                            .formatted(registros.size(),
                                    natureza.isPresent() ? "declarou" : "não declarou"));
        }
        registros = List.copyOf(registros);
    }
}
