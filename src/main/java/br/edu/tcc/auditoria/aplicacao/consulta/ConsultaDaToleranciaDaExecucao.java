package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;

import java.util.Optional;
import java.util.UUID;

// Interface que devolve a tolerância de valor da R05 que uma execução usou (D023); vazio quando a execução é anterior ao registro.
@FunctionalInterface
public interface ConsultaDaToleranciaDaExecucao {

    // Devolve a tolerância registrada da execução, ou vazio se ela não foi registrada.
    Optional<ToleranciaDaExecucao> daExecucao(UUID execucaoId);
}
