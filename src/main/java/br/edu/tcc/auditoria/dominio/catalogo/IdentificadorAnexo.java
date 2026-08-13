package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

/**
 * Identificação do anexo a que um NCM foi vinculado pelo catálogo.
 *
 * <p>Rótulo opaco, validado só quanto à forma. O domínio não conhece a lista de
 * anexos, o que cada um contém nem que tratamento implica — tudo isso chega por
 * importação.</p>
 */
public record IdentificadorAnexo(String valor) {

    public IdentificadorAnexo {
        if (valor == null) {
            throw new RegistroNormativoInvalido("O identificador do anexo não pode ser nulo.");
        }
        if (valor.isBlank()) {
            throw new RegistroNormativoInvalido("O identificador do anexo não pode ser vazio.");
        }
        if (!valor.equals(valor.strip())) {
            throw new RegistroNormativoInvalido(
                    "O identificador do anexo não pode ter espaço em volta: \"%s\".".formatted(valor));
        }
    }
}
