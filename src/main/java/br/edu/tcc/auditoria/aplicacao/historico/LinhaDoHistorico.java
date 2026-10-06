package br.edu.tcc.auditoria.aplicacao.historico;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Representa uma execução no histórico: quando, contra que catálogo e que regras, quem executou, e quantos produtos em cada estado. Executor ausente e situação ausente vêm vazios com o motivo ao lado.
// Emenda de 04/10/2026 (D020): a contagem de produtos também pode faltar. Execução que não gravou os itens que leu — a do comando auditar — não tem produtos a contar, e a contagem vem vazia com o motivo, junto com a situação. Até essa data vinham quatro zeros, que não eram a contagem: eram a ausência dela.
public record LinhaDoHistorico(
        UUID id,
        Instant dataHora,
        String versaoDoCatalogo,
        String versaoDasRegras,
        int documentos,
        int itens,
        Optional<ExecutorRegistrado> executor,
        Optional<Map<EstadoDeConferencia, Integer>> produtosPorEstado,
        Optional<Integer> produtosComPendencia,
        Optional<String> motivoDaContagemAusente,
        Optional<EstadoDeConferencia> situacaoMaisGrave,
        Optional<String> motivoDaSituacaoAusente) {

    // Motivo escrito quando a contagem não foi medida (D020). Vale também para a situação, que sai da contagem.
    public static final String CONTAGEM_NAO_REGISTRADA =
            "não registrado: a execução não gravou os itens que leu — foi feita pelo comando auditar, que "
                    + "não os grava, ou antes de a análise passar a gravá-los —, e sem a lista de itens não há "
                    + "produtos a contar. Zero afirmaria que nenhum produto tem divergência.";

    // Construtor na aridade anterior à D020: a contagem medida, sem motivo de ausência.
    public LinhaDoHistorico(
            UUID id,
            Instant dataHora,
            String versaoDoCatalogo,
            String versaoDasRegras,
            int documentos,
            int itens,
            Optional<ExecutorRegistrado> executor,
            Map<EstadoDeConferencia, Integer> produtosPorEstado,
            int produtosComPendencia,
            Optional<EstadoDeConferencia> situacaoMaisGrave,
            Optional<String> motivoDaSituacaoAusente) {
        this(id, dataHora, versaoDoCatalogo, versaoDasRegras, documentos, itens, executor,
                Optional.ofNullable(produtosPorEstado), Optional.of(produtosComPendencia), Optional.empty(),
                situacaoMaisGrave, motivoDaSituacaoAusente);
    }

    // Motivo escrito quando a execução não tem executor registrado. Não é lacuna: a linha de comando não tem pessoa logada, e execução anterior à Etapa 13 não gravava quem a disparou.
    public static final String EXECUTOR_NAO_REGISTRADO =
            "executor não registrado: execução feita pela linha de comando, ou anterior à Etapa 13, quando "
                    + "a análise não gravava quem a disparou";

    // Valida a linha: identificação, contagens dos quatro estados e o par situação ou motivo.
    public LinhaDoHistorico {
        if (id == null || dataHora == null || versaoDoCatalogo == null || versaoDasRegras == null
                || executor == null || produtosPorEstado == null || produtosComPendencia == null
                || motivoDaContagemAusente == null
                || situacaoMaisGrave == null || motivoDaSituacaoAusente == null) {
            throw new HistoricoInvalido("A linha do histórico veio com campo nulo.");
        }
        if (produtosPorEstado.isPresent() != produtosComPendencia.isPresent()) {
            throw new HistoricoInvalido("As contagens de produtos vêm todas medidas ou todas ausentes.");
        }
        if (produtosPorEstado.isPresent() == motivoDaContagemAusente.isPresent()) {
            throw new HistoricoInvalido("A contagem de produtos vem presente ou com o motivo de faltar, nunca os dois.");
        }
        if (produtosPorEstado.isEmpty() && situacaoMaisGrave.isPresent()) {
            throw new HistoricoInvalido("Sem contagem de produtos não há situação mais grave: ela sai da contagem.");
        }
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            if (produtosPorEstado.isPresent() && !produtosPorEstado.get().containsKey(estado)) {
                throw new HistoricoInvalido(
                        "A linha do histórico precisa dos quatro estados, inclusive os zeros; faltou %s."
                                .formatted(estado));
            }
        }
        if (situacaoMaisGrave.isPresent() == motivoDaSituacaoAusente.isPresent()) {
            throw new HistoricoInvalido("A situação mais grave vem presente ou com o motivo de faltar, nunca os dois.");
        }
        produtosPorEstado = produtosPorEstado.map(Map::copyOf);
    }

    // Representa quem executou: login, nome e se continua ativo.
    public record ExecutorRegistrado(String login, String nome, boolean ativo) {
    }
}
