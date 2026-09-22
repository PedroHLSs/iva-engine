package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.UUID;

/**
 * Execução pedida que não está gravada. Sai como 404.
 *
 * <p>Separada de {@link PedidoInvalido} de propósito: um identificador bem
 * formado que não existe é resposta legítima do sistema — a execução foi apagada,
 * ou o identificador veio de outra instalação —, e não erro de quem perguntou.</p>
 */
public class ExecucaoNaoEncontrada extends RuntimeException {

    public ExecucaoNaoEncontrada(UUID id) {
        super("Não há execução gravada com o identificador %s.".formatted(id));
    }
}
