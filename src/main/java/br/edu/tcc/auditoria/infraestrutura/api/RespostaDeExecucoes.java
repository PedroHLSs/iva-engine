package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/**
 * As execuções gravadas, da mais recente para a mais antiga.
 *
 * <p>{@code limite} vai na resposta porque a listagem é recortada: sem ele, uma
 * resposta com vinte linhas não distingue "há vinte execuções" de "há duzentas e
 * você pediu vinte".</p>
 */
public record RespostaDeExecucoes(List<ExecucaoResumida> execucoes, int quantidade, int limite) {

    /** Quantas execuções a listagem traz quando quem consulta não pede um número. */
    public static final int LIMITE_PADRAO = 25;

    /** Teto do recorte, para que um pedido não varra o histórico inteiro. */
    public static final int LIMITE_MAXIMO = 200;

    public RespostaDeExecucoes {
        if (execucoes == null) {
            throw new RespostaInvalida(
                    "A lista de execuções deve ser vazia quando nenhuma auditoria rodou, nunca nula.");
        }
        if (quantidade != execucoes.size()) {
            throw new RespostaInvalida(
                    "A resposta diz %d execução(ões) e traz %d linha(s).".formatted(
                            quantidade, execucoes.size()));
        }
        execucoes = List.copyOf(execucoes);
    }

    public static RespostaDeExecucoes de(List<ExecucaoResumida> execucoes, int limite) {
        return new RespostaDeExecucoes(execucoes, execucoes.size(), limite);
    }
}
