package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

// Interface responsável por consultar as avaliações que não concluíram numa execução.
public interface ConsultaDeNaoAvaliadas {

    // Retorna as avaliações não concluídas daquela execução, em ordem determinística.
    List<NaoAvaliadaRegistrada> daExecucao(UUID execucaoId);
}
