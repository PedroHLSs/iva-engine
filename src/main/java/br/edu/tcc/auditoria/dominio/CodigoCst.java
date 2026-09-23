package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CodigoCstInvalido;

// Representa o CST de IBS ou de CBS como veio na nota. Só confere que não está vazio nem tem espaço; quais códigos existem e com quem combinam vem do catálogo. É separado do cClassTrib para o compilador não deixar trocar um pelo outro.
public record CodigoCst(String valor) {

    // Valida que o código não seja nulo, vazio nem tenha espaço.
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
