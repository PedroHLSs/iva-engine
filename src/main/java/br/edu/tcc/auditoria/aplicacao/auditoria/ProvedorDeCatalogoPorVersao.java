package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.util.Optional;

/**
 * Porta de carregamento de uma carga de catálogo <strong>identificada</strong>.
 *
 * <h2>Por que não basta o {@link ProvedorDeCatalogo}</h2>
 *
 * <p>Aquele carrega a carga mais recente, que é o certo para auditar: audita-se
 * contra o que se sabe hoje. Este carrega a carga que uma execução registrou, que
 * é o certo para reabrir o resultado dela.</p>
 *
 * <p>A diferença não é conveniência. Reabrir uma análise de março e resolver o
 * tratamento dos produtos com o catálogo carregado em setembro mostraria, ao lado
 * de apontamentos produzidos em março, uma fundamentação que não os produziu — e
 * a pessoa leria os dois como se fossem a mesma coisa. É exatamente o que a D009
 * recusou quando tirou acurácia de dentro da execução, e o que a D003 recusa
 * quando exige que toda consulta normativa se resolva na data do documento: as
 * duas coordenadas precisam bater, a data <em>e</em> a carga.</p>
 *
 * <h2>A carga pode não existir mais, e isso é resposta</h2>
 *
 * <p>Devolve vazio quando nenhuma carga tem aquela versão — banco recomeçado,
 * carga apagada, análise trazida de outra instalação. Vazio não é erro e não vira
 * exceção: a tela diz que não foi possível determinar o tratamento com os dados
 * disponíveis, que é a verdade. O que ela não pode fazer é cair na carga mais
 * recente em silêncio.</p>
 */
public interface ProvedorDeCatalogoPorVersao {

    /** A carga daquela versão, vazio se ela não está mais gravada. */
    Optional<CatalogoParaAuditoria> daVersao(String versao);
}
