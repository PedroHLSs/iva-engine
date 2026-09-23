package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.UUID;

public class ExecucaoNaoEncontrada extends RuntimeException {

    // Construtor que recebe o identificador da execução e chama RuntimeException com a mensagem de erro.
    public ExecucaoNaoEncontrada(UUID id) {
        super("Não há execução gravada com o identificador %s.".formatted(id));
    }
}
