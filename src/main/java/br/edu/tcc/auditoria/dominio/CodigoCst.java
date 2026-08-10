package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CodigoCstInvalido;

/**
 * Código de Situação Tributária de IBS ou de CBS, tal como declarado no
 * documento.
 *
 * <p><strong>Deliberadamente não valida o conjunto de códigos aceitos nem o
 * comprimento esperado.</strong> Quais códigos existem, quantos caracteres têm
 * e quais são compatíveis com cada classificação tributária é conteúdo
 * normativo: entra no sistema por importação de CSV em tempo de execução e é
 * confrontado pelas regras de auditoria, não pelo construtor. Escrever aqui uma
 * lista ou um comprimento fixo transformaria uma suposição em afirmação sobre a
 * legislação.</p>
 *
 * <p>O que o construtor garante é apenas que existe um código: texto não vazio
 * e sem espaço em branco. Um item que não declarou CST se representa com
 * {@code Optional.empty()} em {@link ItemDocumento}, nunca com um
 * {@code CodigoCst} de valor vazio.</p>
 *
 * <p>É um tipo distinto de {@link CodigoClassificacaoTributaria} de propósito:
 * os dois carregam texto, mas trocá-los é erro, e o compilador deve pegá-lo.</p>
 */
public record CodigoCst(String valor) {

    public CodigoCst {
        if (valor == null) {
            throw new CodigoCstInvalido("O código de CST não pode ser nulo.");
        }
        if (valor.isBlank()) {
            throw new CodigoCstInvalido(
                    "O código de CST não pode ser vazio. Ausência de CST se representa com Optional.empty().");
        }
        if (valor.chars().anyMatch(Character::isWhitespace)) {
            throw new CodigoCstInvalido(
                    "O código de CST não pode conter espaço em branco: \"%s\".".formatted(valor));
        }
    }
}
