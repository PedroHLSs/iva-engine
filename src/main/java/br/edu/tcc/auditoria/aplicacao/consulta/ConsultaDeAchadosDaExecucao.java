package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

// Interface responsável por consultar os apontamentos que uma execução específica produziu, com a tratativa no estado atual.
public interface ConsultaDeAchadosDaExecucao {

    // Retorna os apontamentos daquela execução, do mais grave para o menos grave.
    List<AchadoRegistrado> daExecucao(UUID execucaoId);
}
