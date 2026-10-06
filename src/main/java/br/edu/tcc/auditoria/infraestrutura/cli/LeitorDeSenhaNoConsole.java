package br.edu.tcc.auditoria.infraestrutura.cli;

import org.springframework.stereotype.Component;

import java.io.Console;
import java.util.Optional;

// Classe que pede a senha pelo console do sistema, sem eco. Sem console interativo, como num roteiro com a entrada redirecionada, devolve vazio e o comando recusa: tratativa e criação de administrador são atos de uma pessoa no terminal.
@Component
class LeitorDeSenhaNoConsole implements LeitorDeSenha {

    @Override
    public Optional<String> pedir(String pergunta) {
        Console console = System.console();
        if (console == null) {
            return Optional.empty();
        }
        char[] digitada = console.readPassword("%s", pergunta);
        return digitada == null ? Optional.empty() : Optional.of(new String(digitada));
    }
}
