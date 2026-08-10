package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CfopInvalido;

/**
 * Código Fiscal de Operações e Prestações declarado para um item: 4 dígitos.
 *
 * <p>Valida somente a forma. O domínio não conhece a lista de CFOP existentes
 * nem o que cada um significa; essa tabela é conteúdo normativo e chega por
 * importação.</p>
 */
public record Cfop(String valor) {

    private static final int QUANTIDADE_DE_DIGITOS = 4;

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
