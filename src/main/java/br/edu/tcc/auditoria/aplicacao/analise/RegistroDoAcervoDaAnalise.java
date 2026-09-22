package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.UUID;

// Interface responsável por registrar os itens lidos e os arquivos ilegíveis de uma execução de análise.
public interface RegistroDoAcervoDaAnalise {

    /**
     * @param execucaoId a execução que a auditoria acabou de gravar
     * @param itens      todos os itens lidos, inclusive os sem apontamento
     * @param ilegiveis  os arquivos que falharam, na ordem em que falharam;
     *                   vazia quando todos foram lidos
     */
    void registrar(UUID execucaoId, List<ItemDaAnalise> itens, List<ArquivoIlegivel> ilegiveis);
}
