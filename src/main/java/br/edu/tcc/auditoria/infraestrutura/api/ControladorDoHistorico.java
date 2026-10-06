package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.ConsultaDaNaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDaToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;
import br.edu.tcc.auditoria.aplicacao.historico.FiltroDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.ServicoDoHistorico;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

// Controlador de GET /api/analises: o histórico de análises, paginado e filtrado no servidor. A ordem é sempre da mais recente para a mais antiga. Acrescentado na Etapa 13.
@RestController
@RequestMapping("/api/analises")
class ControladorDoHistorico {

    // Tamanho de página quando o pedido não diz.
    static final int TAMANHO_PADRAO = 20;

    private final ServicoDoHistorico historico;
    private final ConsultaDaNaturezaDaCarga naturezas;
    private final ConsultaDaToleranciaDaExecucao tolerancias;

    // Construtor que recebe o serviço do histórico e, desde 04/10/2026 (D021), a consulta da natureza do catálogo.
    // Emenda de 04/10/2026 (D023): recebe também a consulta da tolerância da R05, que vai em cada linha.
    ControladorDoHistorico(ServicoDoHistorico historico, ConsultaDaNaturezaDaCarga naturezas,
                           ConsultaDaToleranciaDaExecucao tolerancias) {
        this.naturezas = naturezas;
        this.tolerancias = tolerancias;
        this.historico = historico;
    }

    // Devolve uma página do histórico com os filtros pedidos; filtro omitido quer dizer "sem filtro".
    @GetMapping
    RespostaDoHistorico listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANHO_PADRAO) int tamanho,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            @RequestParam(required = false) String situacaoMaisGrave,
            @RequestParam(required = false) Integer minimoDeDivergencias,
            @RequestParam(required = false) Integer maximoDeDivergencias,
            @RequestParam(required = false) String executor,
            @RequestParam(defaultValue = "false") boolean semExecutorRegistrado) {

        FiltroDoHistorico filtro = new FiltroDoHistorico(
                Parametros.textoOpcional(de, "de").map(valor -> Parametros.dataObrigatoria(valor, "de")),
                Parametros.textoOpcional(ate, "ate").map(valor -> Parametros.dataObrigatoria(valor, "ate")),
                Optional.ofNullable(Parametros.constante(
                        situacaoMaisGrave, EstadoDeConferencia.class, "situacaoMaisGrave", null)),
                Optional.ofNullable(minimoDeDivergencias),
                Optional.ofNullable(maximoDeDivergencias),
                new FiltroDoHistorico.FiltroDeExecutor(
                        Parametros.textoOpcional(executor, "executor"), semExecutorRegistrado),
                pagina,
                tamanho);
        return RespostaDoHistorico.de(historico.buscar(filtro), naturezas::daVersao, tolerancias::daExecucao);
    }

    // Representa quem executou a análise, ou o motivo de não haver executor registrado.
    record AutoriaExposta(RespostaDoHistorico.ExecutorExposto executor, String motivoDoExecutorAusente) {
    }

    // Devolve quem executou a análise, para a aba de detalhes técnicos.
    @GetMapping("/{id}/autoria")
    AutoriaExposta autoria(@PathVariable UUID id) {
        return historico.executorDe(id)
                .map(executor -> new AutoriaExposta(RespostaDoHistorico.ExecutorExposto.de(executor), null))
                .orElseGet(() -> new AutoriaExposta(null,
                        br.edu.tcc.auditoria.aplicacao.historico.LinhaDoHistorico.EXECUTOR_NAO_REGISTRADO));
    }
}
