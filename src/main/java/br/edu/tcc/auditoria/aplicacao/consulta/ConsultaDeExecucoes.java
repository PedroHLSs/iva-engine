package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura dos recibos de execução gravados.
 *
 * <p>É o que permite reemitir o papel de trabalho de uma rodada meses depois com
 * a identificação que ela tinha na hora — data, versão de catálogo, versão de
 * regras e resumo da entrada.</p>
 */
public interface ConsultaDeExecucoes {

    /** A execução mais recente, vazio se nenhuma auditoria rodou ainda. */
    Optional<ExecucaoAuditoria> maisRecente();

    /** Uma execução pelo identificador, vazio se não existe. */
    Optional<ExecucaoAuditoria> porId(UUID id);

    /** As execuções mais recentes, da mais nova para a mais antiga. */
    List<ExecucaoAuditoria> ultimas(int quantidade);
}
