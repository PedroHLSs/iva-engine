package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.UUID;

/** O que uma análise leu, para quem vai apresentá-la de novo. */
public interface ConsultaDoAcervoDaAnalise {

    /** Os itens lidos, ordenados por documento e número do item. */
    List<ItemDaAnalise> itensDaExecucao(UUID execucaoId);

    /** Os arquivos que não puderam ser lidos, na ordem em que falharam. */
    List<ArquivoIlegivel> arquivosIlegiveis(UUID execucaoId);
}
