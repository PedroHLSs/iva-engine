package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Interface que o papel de trabalho usa para saber quais arquivos uma execução não conseguiu ler (D018). Vazio quer dizer que a leitura não foi registrada; lista vazia, que nenhum arquivo falhou.
@FunctionalInterface
public interface ConsultaDosArquivosNaoLidos {

    // Devolve os arquivos que a execução não leu, na ordem em que falharam, ou vazio se a leitura não foi registrada.
    Optional<List<ArquivoIlegivel>> daExecucao(UUID execucaoId);
}
