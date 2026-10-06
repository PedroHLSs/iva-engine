package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.conferencia.ContagemDeEstados;
import br.edu.tcc.auditoria.aplicacao.historico.LinhaDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.PaginaDoHistorico;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

// Representa uma página do histórico de análises como a API mostra. Cada linha traz a identificação da execução — versão do catálogo e das regras — e os quatro estados separados, inclusive os zeros; nenhum campo soma não concluído a sem divergência. Executor e situação ausentes vêm null com o motivo. Acrescentada na Etapa 13.
// Emenda de 04/10/2026 (D020): a contagem de produtos vem null, com motivoDaContagemAusente, quando a execução não a mediu; e a página diz quantas execuções do resultado estão nesse caso, com a explicação de por que nenhum filtro de quantidade ou de situação as exclui.
public record RespostaDoHistorico(
        List<LinhaExposta> linhas,
        PaginacaoExposta pagina,
        List<ExecutorExposto> executoresConhecidos,
        String rotuloDoFiltroDeSituacao,
        String explicacaoDoFiltroDeSituacao,
        String rotuloDoFiltroDeDivergencias,
        String ordem,
        long execucoesNaoMedidasNoResultado,
        String explicacaoDasNaoMedidas) {

    static final String ROTULO_DA_SITUACAO = "Situação mais grave presente";

    static final String EXPLICACAO_DA_SITUACAO =
            "Uma análise entra no filtro pela situação mais grave que algum produto dela tem, e não pela da "
                    + "maioria. Uma análise com seis produtos sem divergência e um não concluído é \"não foi "
                    + "possível concluir\". Por isso filtrar por \"sem divergência identificada\" traz só as "
                    + "análises em que nenhum produto teve outra situação.";

    static final String ROTULO_DAS_DIVERGENCIAS = "Produtos com possível divergência";

    static final String ORDEM = "da mais recente para a mais antiga";

    static final String EXPLICACAO_DAS_NAO_MEDIDAS =
            "";

    // Representa uma execução no histórico.
    public record LinhaExposta(
            String id,
            Instant dataHora,
            String versaoDoCatalogo,
            String versaoDasRegras,
            int documentos,
            int itens,
            ExecutorExposto executor,
            String motivoDoExecutorAusente,
            List<EstadoContado> produtosPorSituacao,
            Integer produtosComAlgumaVerificacaoNaoConcluida,
            String motivoDaContagemAusente,
            String situacaoMaisGrave,
            String rotuloDaSituacaoMaisGrave,
            String motivoDaSituacaoAusente,
            FaixaDeNatureza natureza,
            ToleranciaExposta toleranciaDeValor) {

        // Emenda de 04/10/2026 (D023): cada linha traz a tolerância de valor da R05 que a execução usou.

        // Emenda de 04/10/2026 (D021): cada linha traz a faixa de procedência do catálogo da execução.

        // Valida o par executor ou motivo, e o par situação ou motivo.
        public LinhaExposta {
            if ((executor == null) == (motivoDoExecutorAusente == null)) {
                throw new RespostaInvalida("A linha traz o executor ou o motivo de não haver, nunca os dois.");
            }
            if ((situacaoMaisGrave == null) == (motivoDaSituacaoAusente == null)) {
                throw new RespostaInvalida("A linha traz a situação mais grave ou o motivo de faltar, nunca os dois.");
            }
            if ((produtosPorSituacao == null) == (motivoDaContagemAusente == null)) {
                throw new RespostaInvalida("A linha traz a contagem de produtos ou o motivo de faltar, nunca os dois.");
            }
            if ((produtosPorSituacao == null) != (produtosComAlgumaVerificacaoNaoConcluida == null)) {
                throw new RespostaInvalida("As contagens de produtos vêm todas ou nenhuma.");
            }
            if (produtosPorSituacao != null && produtosPorSituacao.size() != 4) {
                throw new RespostaInvalida("A linha traz os quatro estados, inclusive os zeros.");
            }
            if (natureza == null) {
                throw new RespostaInvalida("A linha traz a faixa de procedência do catálogo (D021).");
            }
            if (toleranciaDeValor == null) {
                throw new RespostaInvalida("A linha traz a tolerância da R05, ou o motivo de não haver (D023).");
            }
        }
    }

    // Representa quem executou.
    public record ExecutorExposto(String login, String nome, boolean ativo) {

        // Método estático que converte o executor da aplicação.
        static ExecutorExposto de(LinhaDoHistorico.ExecutorRegistrado executor) {
            return new ExecutorExposto(executor.login(), executor.nome(), executor.ativo());
        }
    }

    // Representa a paginação: número, tamanho, total de linhas e de páginas.
    public record PaginacaoExposta(int numero, int tamanho, long totalDeLinhas, long totalDePaginas) {
    }

    // Método estático que monta a resposta a partir da página da aplicação. Emenda de 04/10/2026 (D021): recebe de onde ler a natureza do catálogo de cada linha.
    // Método estático que monta a resposta com a natureza e a tolerância de cada linha (D023).
    static RespostaDoHistorico de(PaginaDoHistorico pagina, Function<String, NaturezaDaCarga> naturezaDaVersao,
                                  Function<UUID, Optional<ToleranciaDaExecucao>> toleranciaDaExecucao) {
        List<LinhaExposta> linhas = pagina.linhas().stream().map(linha -> new LinhaExposta(
                linha.id().toString(),
                linha.dataHora(),
                linha.versaoDoCatalogo(),
                linha.versaoDasRegras(),
                linha.documentos(),
                linha.itens(),
                linha.executor().map(ExecutorExposto::de).orElse(null),
                linha.executor().isPresent() ? null : LinhaDoHistorico.EXECUTOR_NAO_REGISTRADO,
                linha.produtosPorEstado().map(porEstado -> EstadoContado.de(new ContagemDeEstados(porEstado)))
                        .orElse(null),
                linha.produtosComPendencia().orElse(null),
                linha.motivoDaContagemAusente().orElse(null),
                linha.situacaoMaisGrave().map(Enum::name).orElse(null),
                linha.situacaoMaisGrave().map(estado -> estado.rotulo()).orElse(null),
                linha.motivoDaSituacaoAusente().orElse(null),
                FaixaDeNatureza.de(naturezaDaVersao.apply(linha.versaoDoCatalogo()), linha.versaoDoCatalogo()),
                ToleranciaExposta.de(toleranciaDaExecucao.apply(linha.id()))))
                .toList();
        long paginas = pagina.totalDeLinhas() == 0 ? 0 : (pagina.totalDeLinhas() + pagina.tamanho() - 1) / pagina.tamanho();
        return new RespostaDoHistorico(
                linhas,
                new PaginacaoExposta(pagina.pagina(), pagina.tamanho(), pagina.totalDeLinhas(), paginas),
                pagina.executoresConhecidos().stream().map(ExecutorExposto::de).toList(),
                ROTULO_DA_SITUACAO,
                EXPLICACAO_DA_SITUACAO,
                ROTULO_DAS_DIVERGENCIAS,
                ORDEM,
                pagina.execucoesNaoMedidasNoResultado(),
                EXPLICACAO_DAS_NAO_MEDIDAS);
    }
}
