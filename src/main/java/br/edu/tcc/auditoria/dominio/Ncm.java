package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.NcmInvalido;

// Representa o NCM do item: 8 dígitos. Só confere o formato; quais NCM existem e em que anexo estão vem do catálogo importado.
public record Ncm(String valor) {

    private static final int QUANTIDADE_DE_DIGITOS = 8;

    // Valida que o NCM tenha exatamente 8 dígitos.
    public Ncm {
        if (valor == null) {
            throw new NcmInvalido("O NCM não pode ser nulo.");
        }
        if (valor.length() != QUANTIDADE_DE_DIGITOS) {
            throw new NcmInvalido(
                    "O NCM deve ter %d dígitos, mas veio com %d: \"%s\"."
                            .formatted(QUANTIDADE_DE_DIGITOS, valor.length(), valor));
        }
        if (!valor.chars().allMatch(caractere -> caractere >= '0' && caractere <= '9')) {
            throw new NcmInvalido("O NCM deve conter somente dígitos: \"%s\".".formatted(valor));
        }
    }
}
