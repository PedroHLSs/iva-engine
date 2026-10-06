package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.Map;

// Representa uma execução na lista, com as versões do catálogo e das regras e o total de achados por gravidade. As quatro gravidades vêm sempre, até as que ficaram em zero, e sempre na mesma ordem.
public record ExecucaoResumida(
        String id,
        Instant dataHora,
        String hashEntrada,
        String versaoCatalogo,
        String versaoConjuntoRegras,
        int quantidadeDocumentos,
        int quantidadeItens,
        int achados,
        Map<String, Integer> achadosPorSeveridade,
        FaixaDeNatureza natureza,
        ToleranciaExposta toleranciaDeValor) {

    // Emenda de 04/10/2026 (D023): traz a tolerância de valor da R05 que a execução usou, com a origem.

    // Emenda de 04/10/2026 (D021): cada execução da lista traz a faixa de procedência do catálogo dela.

    // Valida que a execução tenha identificador, data e hora, e a contagem por gravidade.
    public ExecucaoResumida {
        if (toleranciaDeValor == null) {
            throw new RespostaInvalida(
                    "A resposta traz a tolerância de valor da R05 que a execução usou, ou o motivo de não haver "
                            + "(D023): duas execuções com tolerâncias diferentes dão resultados diferentes.");
        }
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência do catálogo (D021). Dado de "
                            + "demonstração sem aviso é afirmação falsa sobre a lei.");
        }
        if (id == null || dataHora == null) {
            throw new RespostaInvalida("A execução resumida precisa de identificador e data e hora.");
        }
        if (achadosPorSeveridade == null || achadosPorSeveridade.isEmpty()) {
            throw new RespostaInvalida(
                    "A contagem por severidade precisa trazer zero onde não houve apontamento, nunca "
                            + "vir vazia.");
        }
    }
}
