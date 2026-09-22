package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

/**
 * Os itens que uma execução leu, com o conteúdo declarado de cada um.
 *
 * <p>Distinta de {@link ConsultaDeDocumentos}, que devolve só o cabeçalho do
 * documento. A conferência de enquadramento é sobre o item — NCM, cClassTrib,
 * base, alíquota e valor —, e é ele que precisa vir inteiro.</p>
 *
 * <p>A lista inclui <strong>todos</strong> os itens lidos, e não só os que
 * produziram apontamento ou pendência. Era essa a lacuna que a V7 fechou.</p>
 */
public interface ConsultaDeItensDaExecucao {

    /** Ordenados por documento e número do item, para a tela sair sempre igual. */
    List<DadosDoItem> daExecucao(UUID execucaoId);
}
