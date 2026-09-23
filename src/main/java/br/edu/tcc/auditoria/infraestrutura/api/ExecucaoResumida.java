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
        Map<String, Integer> achadosPorSeveridade) {

    // Valida que a execução tenha identificador, data e hora, e a contagem por gravidade.
    public ExecucaoResumida {
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
