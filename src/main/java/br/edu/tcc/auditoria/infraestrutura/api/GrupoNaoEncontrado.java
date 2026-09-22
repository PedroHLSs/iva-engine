package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.UUID;

/**
 * Combinação de NCM, cClassTrib e situação que não forma grupo naquela análise.
 *
 * <p>Sai como 404 pelo mesmo motivo dos outros dois: é identificador bem formado
 * que não existe, e não erro de quem perguntou. Acontece legitimamente quando a
 * análise é reprocessada e os produtos mudam de situação — o grupo que estava
 * aberto na tela deixa de existir, e dizer isso é melhor que devolver uma lista
 * vazia com cara de grupo sem produtos.</p>
 */
public class GrupoNaoEncontrado extends RuntimeException {

    public GrupoNaoEncontrado(UUID analiseId) {
        super(("A análise %s não tem grupo com esse NCM, esse cClassTrib e essa situação. Se a análise "
                + "foi refeita, os produtos podem ter mudado de situação e o grupo deixado de "
                + "existir.").formatted(analiseId));
    }
}
