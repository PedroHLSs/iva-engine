package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ChaveAcessoInvalida;

/**
 * Chave de acesso de um documento fiscal eletrônico: 44 dígitos.
 *
 * <p>Valida apenas a forma — quantidade de dígitos. Não interpreta as posições
 * da chave, não extrai UF, modelo, CNPJ nem número a partir dela, e não confere
 * dígito verificador: o significado posicional da chave é definido pelo leiaute
 * e este projeto não afirma leiaute em código.</p>
 *
 * <p>Não normaliza a entrada. Espaço em branco em volta faz a chave ser
 * rejeitada; limpar o texto lido do XML é responsabilidade da infraestrutura,
 * não do domínio.</p>
 */
public record ChaveAcesso(String valor) {

    private static final int QUANTIDADE_DE_DIGITOS = 44;

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

    private static boolean contemSomenteDigitos(String texto) {
        return texto.chars().allMatch(caractere -> caractere >= '0' && caractere <= '9');
    }
}
