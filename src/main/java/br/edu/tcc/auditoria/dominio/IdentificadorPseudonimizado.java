package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.IdentificadorPseudonimizadoInvalido;

/**
 * Identificador de um participante do documento — emitente ou destinatário —
 * na forma de pseudônimo.
 *
 * <p><strong>Este tipo existe para tornar impossível guardar CNPJ, CPF, razão
 * social ou endereço dentro do domínio.</strong> Ele aceita exclusivamente um
 * resumo criptográfico de 256 bits em hexadecimal minúsculo: 64 caracteres em
 * {@code 0-9a-f}. Nenhum CNPJ (14 dígitos), CPF (11 dígitos), nome empresarial
 * ou logradouro satisfaz esse formato, de modo que a tentativa de passar o
 * valor original falha na construção, e não silenciosamente.</p>
 *
 * <p>O cálculo do resumo é responsabilidade da infraestrutura, que é a única
 * camada que vê o dado original. O domínio recebe o pseudônimo pronto, não sabe
 * revertê-lo e não precisa: para auditar coerência de IBS/CBS basta saber se
 * dois documentos têm o mesmo participante, e a igualdade do pseudônimo
 * responde isso.</p>
 */
public record IdentificadorPseudonimizado(String valor) {

    private static final int COMPRIMENTO_ESPERADO = 64;

    public IdentificadorPseudonimizado {
        if (valor == null) {
            throw new IdentificadorPseudonimizadoInvalido(
                    "O identificador pseudonimizado não pode ser nulo.");
        }
        if (valor.length() != COMPRIMENTO_ESPERADO) {
            // O valor recusado não é reproduzido: se veio errado, pode ser justamente
            // o dado em texto claro que este tipo existe para barrar.
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

    private static boolean ehHexadecimalMinusculo(String texto) {
        return texto.chars().allMatch(caractere ->
                (caractere >= '0' && caractere <= '9') || (caractere >= 'a' && caractere <= 'f'));
    }
}
