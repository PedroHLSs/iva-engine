package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa a lista das execuções gravadas, da mais nova para a mais antiga. O limite vai junto, para quem lê saber se a lista foi cortada.
public record RespostaDeExecucoes(List<ExecucaoResumida> execucoes, int quantidade, int limite) {

    // Quantas execuções a lista traz quando ninguém pede um número.
    public static final int LIMITE_PADRAO = 25;

    // Máximo de execuções por pedido, para ninguém varrer o histórico inteiro.
    public static final int LIMITE_MAXIMO = 200;

    // Valida que a lista exista e que a quantidade bata com o número de linhas.
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

    // Método estático que cria a resposta contando as execuções da lista.
    public static RespostaDeExecucoes de(List<ExecucaoResumida> execucoes, int limite) {
        return new RespostaDeExecucoes(execucoes, execucoes.size(), limite);
    }
}
