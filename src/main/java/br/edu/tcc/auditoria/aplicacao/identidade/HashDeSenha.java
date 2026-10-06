package br.edu.tcc.auditoria.aplicacao.identidade;

// Representa o hash da senha, do jeito que é gravado. Também não aparece no toString: o hash não devolve a senha, mas permite tentar adivinhá-la fora do sistema.
public record HashDeSenha(String valor) {

    // Valida que o hash exista.
    public HashDeSenha {
        if (valor == null || valor.isBlank()) {
            throw new IdentidadeInvalida("O hash da senha não pode ser vazio.");
        }
    }

    // Nunca mostra o valor.
    @Override
    public String toString() {
        return "HashDeSenha[omitido]";
    }
}
