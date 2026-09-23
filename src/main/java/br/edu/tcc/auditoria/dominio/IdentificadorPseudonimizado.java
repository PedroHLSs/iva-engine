package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.IdentificadorPseudonimizadoInvalido;

// Representa o pseudônimo de um participante da nota (emitente ou destinatário). Só aceita 64 caracteres hexadecimais, então CNPJ, CPF, nome ou endereço não conseguem entrar no domínio.
public record IdentificadorPseudonimizado(String valor) {

    private static final int COMPRIMENTO_ESPERADO = 64;

    // Valida que o pseudônimo tenha 64 caracteres, só com hexadecimal minúsculo.
    public IdentificadorPseudonimizado {
        if (valor == null) {
            throw new IdentificadorPseudonimizadoInvalido(
                    "O identificador pseudonimizado não pode ser nulo.");
        }
        if (valor.length() != COMPRIMENTO_ESPERADO) {
            // O valor recusado não aparece na mensagem, porque pode ser justamente o CNPJ ou CPF que este tipo barra.
            throw new IdentificadorPseudonimizadoInvalido(
                    ("O identificador pseudonimizado deve ter %d caracteres hexadecimais, mas veio "
                            + "com %d. O domínio não aceita CNPJ, CPF, razão social nem endereço: "
                            + "a pseudonimização é feita na infraestrutura.")
                            .formatted(COMPRIMENTO_ESPERADO, valor.length()));
        }
        if (!ehHexadecimalMinusculo(valor)) {
            throw new IdentificadorPseudonimizadoInvalido(
                    "O identificador pseudonimizado deve conter somente hexadecimal minúsculo (0-9, a-f).");
        }
    }

    // Método auxiliar que confere se o texto só tem dígitos de 0 a 9 e letras de a a f.
    private static boolean ehHexadecimalMinusculo(String texto) {
        return texto.chars().allMatch(caractere ->
                (caractere >= '0' && caractere <= '9') || (caractere >= 'a' && caractere <= 'f'));
    }
}
