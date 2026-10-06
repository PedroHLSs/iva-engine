package br.edu.tcc.auditoria.aplicacao.tratativa;

import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;

import java.util.UUID;

// Representa quem registrou uma tratativa: identificador, login, nome e se ainda está ativo. Usuário desativado continua aparecendo como autor.
public record AutorDaTratativa(UUID id, String login, String nome, boolean ativo) {

    // Valida que o autor tenha identificador, login e nome.
    public AutorDaTratativa {
        if (id == null || login == null || login.isBlank() || nome == null || nome.isBlank()) {
            throw new TratativaInvalida("O autor da tratativa precisa de identificador, login e nome.");
        }
    }
}
