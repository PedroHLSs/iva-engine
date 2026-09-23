package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.UUID;

public class GrupoNaoEncontrado extends RuntimeException {

    // Construtor que recebe o identificador da análise e chama RuntimeException com a mensagem de erro.
    public GrupoNaoEncontrado(UUID analiseId) {
        super(("A análise %s não tem grupo com esse NCM, esse cClassTrib e essa situação. Se a análise "
                + "foi refeita, os produtos podem ter mudado de situação e o grupo deixado de "
                + "existir.").formatted(analiseId));
    }
}
