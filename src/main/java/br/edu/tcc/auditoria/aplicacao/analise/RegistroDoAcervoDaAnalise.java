package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.UUID;

/**
 * Grava o que uma análise leu, ao lado do que a auditoria já grava.
 *
 * <p>Não substitui nem embrulha {@code RepositorioDaAuditoria}: o pipeline da
 * Etapa 5 continua gravando execução, documentos, itens, apontamentos e
 * avaliações não concluídas exatamente como antes. Esta porta acrescenta as duas
 * coisas que faltavam para a análise poder ser reaberta — a lista dos itens que
 * ela leu e os arquivos que ela não conseguiu ler.</p>
 */
public interface RegistroDoAcervoDaAnalise {

    /**
     * @param execucaoId a execução que a auditoria acabou de gravar
     * @param itens      todos os itens lidos, inclusive os sem apontamento
     * @param ilegiveis  os arquivos que falharam, na ordem em que falharam;
     *                   vazia quando todos foram lidos
     */
    void registrar(UUID execucaoId, List<ItemDaAnalise> itens, List<ArquivoIlegivel> ilegiveis);
}
