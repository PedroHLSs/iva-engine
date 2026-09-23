package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

// Interface responsável por consultar todos os itens que uma execução leu, com o conteúdo declarado de cada um.
public interface ConsultaDeItensDaExecucao {

    // Retorna os itens ordenados por documento e número do item, para a tela sair sempre igual.
    List<DadosDoItem> daExecucao(UUID execucaoId);
}
