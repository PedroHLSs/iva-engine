package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CfopInvalido;

// Representa o CFOP do item: 4 dígitos. Só confere o formato; quais CFOP existem não fica no código.
public record Cfop(String valor) {

    private static final int QUANTIDADE_DE_DIGITOS = 4;

    // Valida que o CFOP tenha exatamente 4 dígitos.
    public Cfop {
        if (valor == null) {
            throw new CfopInvalido("O CFOP não pode ser nulo.");
        }
        if (valor.length() != QUANTIDADE_DE_DIGITOS) {
            throw new CfopInvalido(
                    "O CFOP deve ter %d dígitos, mas veio com %d: \"%s\"."
                            .formatted(QUANTIDADE_DE_DIGITOS, valor.length(), valor));
        }
        if (!valor.chars().allMatch(caractere -> caractere >= '0' && caractere <= '9')) {
            throw new CfopInvalido("O CFOP deve conter somente dígitos: \"%s\".".formatted(valor));
        }
    }
}
