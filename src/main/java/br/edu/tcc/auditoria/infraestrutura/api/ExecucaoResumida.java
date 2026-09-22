package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.Map;

/**
 * Uma execução na listagem, com o que identifica a rodada e os totais por
 * severidade.
 *
 * <p>{@code versaoCatalogo} e {@code versaoConjuntoRegras} vão aqui, e não só no
 * detalhe, porque é o que permite comparar duas rodadas sem abrir as duas: um
 * apontamento que hoje não procede mais — porque a tabela mudou — é
 * indistinguível de um erro do sistema quando o relatório não diz contra o que foi
 * produzido (D007).</p>
 *
 * <p>{@code achadosPorSeveridade} traz as quatro severidades sempre, inclusive as
 * que ficaram em zero, e na ordem de declaração do enum — não na ordem de iteração
 * de um mapa. Severidade omitida obrigaria o leitor a adivinhar se não houve
 * apontamento ou se ninguém olhou, e ordem instável faria duas chamadas iguais
 * responderem diferente.</p>
 */
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
