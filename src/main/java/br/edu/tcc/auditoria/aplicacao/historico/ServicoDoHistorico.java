package br.edu.tcc.auditoria.aplicacao.historico;

import br.edu.tcc.auditoria.aplicacao.conferencia.ConferenciaDaAnalise;
import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.MontadorDaConferencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.ResumoDaConferencia;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Serviço do histórico de análises. Antes de buscar, grava o resumo das execuções que ainda não têm, calculado pelo mesmo MontadorDaConferencia que monta a tela: a tradução de desfecho em estado continua num lugar só. Acrescentado na Etapa 13.
public final class ServicoDoHistorico {

    // Motivo escrito quando a execução não tem produto nenhum a contar.
    // Emenda de 04/10/2026 (D020): passou a valer só para a execução que não leu nenhum item, em que zero produtos é medição. A execução que não gravou os itens que leu fica com LinhaDoHistorico.CONTAGEM_NAO_REGISTRADA. Até essa data este texto cobria as duas, e o resumo gravava zeros nas duas.
    static final String SEM_PRODUTOS =
            "a execução não leu nenhum item: nenhum documento foi encontrado, ou nenhum arquivo pôde ser lido";

    private final AcervoDoHistorico acervo;
    private final MontadorDaConferencia montador;
    private final Clock relogio;

    // Construtor que recebe o acervo do histórico, o montador da conferência e o relógio.
    public ServicoDoHistorico(AcervoDoHistorico acervo, MontadorDaConferencia montador, Clock relogio) {
        if (acervo == null || montador == null || relogio == null) {
            throw new HistoricoInvalido("O serviço do histórico precisa do acervo, do montador e do relógio.");
        }
        this.acervo = acervo;
        this.montador = montador;
        this.relogio = relogio;
    }

    // Grava os resumos que faltam e devolve a página filtrada.
    public PaginaDoHistorico buscar(FiltroDoHistorico filtro) {
        if (filtro == null) {
            throw new HistoricoInvalido("Não há filtro.");
        }
        completarResumos();
        return acervo.buscar(filtro);
    }

    // Registra quem disparou a análise pela web.
    public void registrarExecutor(UUID execucaoId, UUID usuarioId) {
        acervo.registrarExecutor(execucaoId, usuarioId, relogio.instant());
    }

    // Devolve quem executou a análise, ou vazio quando não há executor registrado.
    public Optional<LinhaDoHistorico.ExecutorRegistrado> executorDe(UUID execucaoId) {
        return acervo.executorDe(execucaoId);
    }

    // Método auxiliar que calcula e grava o resumo de cada execução que ainda não tem. Sem os itens lidos registrados, grava a ausência da contagem, e não zeros (D020).
    private void completarResumos() {
        for (UUID execucaoId : acervo.execucoesSemResumo()) {
            if (!acervo.contagemMedida(execucaoId)) {
                acervo.gravarResumo(execucaoId, Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(LinhaDoHistorico.CONTAGEM_NAO_REGISTRADA), relogio.instant());
                continue;
            }
            Optional<ConferenciaDaAnalise> conferencia = montador.daExecucao(execucaoId);
            Map<EstadoDeConferencia, Integer> porEstado = new EnumMap<>(EstadoDeConferencia.class);
            int comPendencia = 0;
            if (conferencia.isPresent()) {
                ResumoDaConferencia resumo = conferencia.get().resumo();
                for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
                    porEstado.put(estado, resumo.produtosPorSituacao().quantidadeDe(estado));
                }
                comPendencia = resumo.produtosComAlgumaNaoConcluida();
            } else {
                for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
                    porEstado.put(estado, 0);
                }
            }
            Optional<EstadoDeConferencia> maisGrave = situacaoMaisGrave(porEstado);
            acervo.gravarResumo(execucaoId, Optional.of(porEstado), Optional.of(comPendencia), maisGrave,
                    maisGrave.isPresent() ? Optional.empty() : Optional.of(SEM_PRODUTOS), relogio.instant());
        }
    }

    // Método estático que devolve o estado mais forte presente, pela ordem de precedência do enum, e não o da maioria.
    static Optional<EstadoDeConferencia> situacaoMaisGrave(Map<EstadoDeConferencia, Integer> porEstado) {
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            if (porEstado.getOrDefault(estado, 0) > 0) {
                return Optional.of(estado);
            }
        }
        return Optional.empty();
    }
}
