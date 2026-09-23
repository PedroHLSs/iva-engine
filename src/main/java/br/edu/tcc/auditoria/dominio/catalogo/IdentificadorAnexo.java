package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

// Representa a identificação do anexo a que um NCM foi vinculado; é um rótulo opaco, e o domínio não conhece a lista de anexos.
public record IdentificadorAnexo(String valor) {

    // Valida que o identificador não seja nulo, vazio nem tenha espaço em volta.
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
