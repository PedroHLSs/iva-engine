package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CodigoClassificacaoTributariaInvalido;

/**
 * Código de classificação tributária ({@code cClassTrib}) tal como declarado no
 * documento.
 *
 * <p><strong>Deliberadamente não valida o conjunto de códigos aceitos nem o
 * comprimento esperado</strong>, pela mesma razão descrita em
 * {@link CodigoCst}: a tabela de códigos, o vínculo de cada um com o CST e a
 * vigência de cada combinação são conteúdo normativo, carregado por importação
 * de CSV.</p>
 *
 * <p>O construtor garante apenas que existe um código: texto não vazio e sem
 * espaço em branco.</p>
 */
public record CodigoClassificacaoTributaria(String valor) {

    public CodigoClassificacaoTributaria {
        if (valor == null) {
            throw new CodigoClassificacaoTributariaInvalido(
                    "O código de classificação tributária não pode ser nulo.");
        }
        if (valor.isBlank()) {
            throw new CodigoClassificacaoTributariaInvalido(
                    "O código de classificação tributária não pode ser vazio. "
                            + "Ausência se representa com Optional.empty().");
        }
        if (valor.chars().anyMatch(Character::isWhitespace)) {
            throw new CodigoClassificacaoTributariaInvalido(
                    "O código de classificação tributária não pode conter espaço em branco: \"%s\"."
                            .formatted(valor));
        }
    }
}
