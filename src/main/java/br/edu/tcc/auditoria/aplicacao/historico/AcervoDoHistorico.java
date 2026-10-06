package br.edu.tcc.auditoria.aplicacao.historico;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Interface responsável por guardar e consultar o histórico no banco: quem executou cada análise, o resumo por estado de cada execução, e a página filtrada. A paginação e os filtros acontecem no banco, e não no navegador.
public interface AcervoDoHistorico {

    // Registra quem disparou a análise.
    void registrarExecutor(UUID execucaoId, UUID usuarioId, Instant quando);

    // Devolve as execuções que ainda não têm resumo gravado.
    List<UUID> execucoesSemResumo();

    // Grava o resumo de uma execução; se outro pedido já gravou, fica o que já estava.
    // Emenda de 04/10/2026 (D020): as contagens vêm vazias quando a execução não registrou os itens que leu, e o banco grava nulo, nunca zero. Um resumo sem contagem é substituído se a execução passar a ter itens.
    void gravarResumo(UUID execucaoId, Optional<Map<EstadoDeConferencia, Integer>> produtosPorEstado,
            Optional<Integer> produtosComPendencia, Optional<EstadoDeConferencia> situacaoMaisGrave,
            Optional<String> motivoDaSituacaoAusente, Instant quando);

    // D020: diz se os produtos da execução podem ser contados — ela gravou os itens que leu, ou não leu item nenhum.
    boolean contagemMedida(UUID execucaoId);

    // Devolve quem executou a análise, se alguém foi registrado.
    Optional<LinhaDoHistorico.ExecutorRegistrado> executorDe(UUID execucaoId);

    // Devolve a página do histórico que o filtro pede, da mais recente para a mais antiga.
    PaginaDoHistorico buscar(FiltroDoHistorico filtro);
}
