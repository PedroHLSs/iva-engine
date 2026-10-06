package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Interface responsável por consultar as análises, retornando os itens lidos
public interface ConsultaDoAcervoDaAnalise {

    //Ordenar os itens da execução na ordem lida
    List<ItemDaAnalise> itensDaExecucao(UUID execucaoId);

    //Mesma coisa, mas ordenando os ilegíveis
    // Emenda de 04/10/2026 (D018): vazio quer dizer que a leitura desta execução não foi registrada, e então não se sabe se algum arquivo falhou; lista vazia quer dizer que nenhum falhou. Até essa data o retorno era a lista, e uma execução da CLI, que não gravava as falhas, saía como lista vazia.
    Optional<List<ArquivoIlegivel>> arquivosIlegiveis(UUID execucaoId);

    // D019 (04/10/2026): quantos documentos repetidos, com o mesmo conteúdo, a execução descartou; vazio quando a leitura não foi registrada, ou foi registrada antes dessa contagem existir.
    Optional<Integer> documentosRepetidosDescartados(UUID execucaoId);
}
