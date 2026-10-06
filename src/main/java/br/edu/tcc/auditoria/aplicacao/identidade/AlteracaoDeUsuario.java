package br.edu.tcc.auditoria.aplicacao.identidade;

import java.util.Optional;

// Representa o que um administrador pediu para mudar num usuário. Campo vazio quer dizer "não mexer".
public record AlteracaoDeUsuario(
        Optional<String> nome,
        Optional<Perfil> perfil,
        Optional<Boolean> ativo,
        Optional<SenhaInformada> novaSenha) {

    // Valida que nenhum campo venha nulo.
    public AlteracaoDeUsuario {
        if (nome == null || perfil == null || ativo == null || novaSenha == null) {
            throw new IdentidadeInvalida(
                    "Campo que não muda se representa com Optional.empty(), nunca com nulo.");
        }
    }
}
