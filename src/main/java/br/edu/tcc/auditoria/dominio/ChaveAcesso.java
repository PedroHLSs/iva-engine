package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ChaveAcessoInvalida;

// Representa a chave de acesso da nota: 44 dígitos. Só confere o formato; não tira dela UF, CNPJ ou número, e não aceita espaço em volta.
public record ChaveAcesso(String valor) {

    private static final int QUANTIDADE_DE_DIGITOS = 44;

    // Valida que a chave tenha exatamente 44 dígitos.
    public ChaveAcesso {
        if (valor == null) {
            throw new ChaveAcessoInvalida("A chave de acesso não pode ser nula.");
        }
        if (valor.length() != QUANTIDADE_DE_DIGITOS) {
            throw new ChaveAcessoInvalida(
                    "A chave de acesso deve ter %d dígitos, mas veio com %d."
                            .formatted(QUANTIDADE_DE_DIGITOS, valor.length()));
        }
        if (!contemSomenteDigitos(valor)) {
            // A chave não é reproduzida na mensagem: ela carrega o CNPJ do emitente.
            throw new ChaveAcessoInvalida("A chave de acesso deve conter somente dígitos.");
        }
    }

    // Método auxiliar que confere se o texto só tem dígitos.
    private static boolean contemSomenteDigitos(String texto) {
        return texto.chars().allMatch(caractere -> caractere >= '0' && caractere <= '9');
    }
}
