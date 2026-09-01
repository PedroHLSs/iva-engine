package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura dos apontamentos que uma execução produziu.
 *
 * <p>Separada de {@link ConsultaDeAchados} porque a pergunta é outra. Aquela
 * responde "o que está apontado hoje"; esta responde "o que a rodada de tal dia
 * apontou". A diferença importa porque o apontamento não é regravado a cada
 * rodada: sem este recorte, o papel de trabalho de uma execução antiga traria os
 * apontamentos da mais recente, e as contagens do resumo não bateriam com as
 * linhas da planilha.</p>
 *
 * <p>A tratativa vem junto e reflete o estado <em>atual</em> — que é o que se
 * quer: o papel de trabalho mostra o que foi encontrado naquela rodada e o que
 * já se decidiu sobre isso até agora.</p>
 */
public interface ConsultaDeAchadosDaExecucao {

    /** Apontamentos daquela execução, do mais grave para o menos grave. */
    List<AchadoRegistrado> daExecucao(UUID execucaoId);
}
