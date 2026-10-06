package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import java.util.Optional;
import java.util.UUID;

// Interface que o papel de trabalho usa para saber quantos documentos repetidos, com o mesmo conteúdo, o lote de uma execução descartou (D019). Vazio quer dizer que a execução não registrou essa contagem.
@FunctionalInterface
public interface ConsultaDosDocumentosRepetidos {

    // Devolve quantas cópias foram descartadas, ou vazio se a contagem não foi registrada.
    Optional<Integer> daExecucao(UUID execucaoId);
}
