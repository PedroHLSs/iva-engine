package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.UUID;

// Interface responsável por registrar os itens lidos e os arquivos ilegíveis de uma execução de análise.
public interface RegistroDoAcervoDaAnalise {

    // Registra, para a execução que acabou de ser gravada, todos os itens lidos, inclusive os sem apontamento, e os arquivos que falharam, na ordem em que falharam.
    void registrar(UUID execucaoId, List<ItemDaAnalise> itens, List<ArquivoIlegivel> ilegiveis);
}
