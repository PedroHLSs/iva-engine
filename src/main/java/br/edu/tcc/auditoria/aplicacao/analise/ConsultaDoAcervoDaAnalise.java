package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.UUID;

// Interface responsável por consultar as análises, retornando os itens lidos
public interface ConsultaDoAcervoDaAnalise {

    //Ordenar os itens da execução na ordem lida
    List<ItemDaAnalise> itensDaExecucao(UUID execucaoId);

    //Mesma coisa, mas ordenando os ilegíveis
    List<ArquivoIlegivel> arquivosIlegiveis(UUID execucaoId);
}
