package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

/** Porta de leitura das avaliações que não concluíram numa execução. */
public interface ConsultaDeNaoAvaliadas {

    /** As avaliações não concluídas daquela execução, em ordem determinística. */
    List<NaoAvaliadaRegistrada> daExecucao(UUID execucaoId);
}
