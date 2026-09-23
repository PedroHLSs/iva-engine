package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CodigoClassificacaoTributariaInvalido;

// Representa o cClassTrib do item como veio na nota. Só confere que não está vazio nem tem espaço; quais códigos existem vem do catálogo importado.
public record CodigoClassificacaoTributaria(String valor) {

    // Valida que o código não seja nulo, vazio nem tenha espaço.
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
