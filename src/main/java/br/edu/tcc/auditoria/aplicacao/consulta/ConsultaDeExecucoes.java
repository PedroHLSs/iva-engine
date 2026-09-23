package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Interface responsável por consultar os recibos de execução gravados, para reemitir o resultado de uma rodada antiga.
public interface ConsultaDeExecucoes {

    // Retorna a execução mais recente, ou vazio se nenhuma auditoria rodou ainda.
    Optional<ExecucaoAuditoria> maisRecente();

    // Retorna uma execução pelo identificador, ou vazio se não existe.
    Optional<ExecucaoAuditoria> porId(UUID id);

    // Retorna as execuções mais recentes, da mais nova para a mais antiga.
    List<ExecucaoAuditoria> ultimas(int quantidade);
}
