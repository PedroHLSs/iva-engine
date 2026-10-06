package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.CorrecoesDeAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ResultadoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.VinculosDaAnalise;
import br.edu.tcc.auditoria.aplicacao.historico.ServicoDoHistorico;
import br.edu.tcc.auditoria.infraestrutura.seguranca.UsuarioAutenticado;
import br.edu.tcc.auditoria.infraestrutura.upload.AreaDaAnalise;
import br.edu.tcc.auditoria.infraestrutura.upload.LimitesDeUpload;
import br.edu.tcc.auditoria.infraestrutura.upload.PacoteRecusado;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

// Controlador da correção de uma análise. Dado já auditado não é editável: corrigir a entrada é enviar de novo, o que gera uma análise nova, com o próprio hash de entrada, e o vínculo à anterior fica registrado. A anterior não muda. Fica fora do ControladorDeAnalises para não mexer nele, e usa o mesmo ServicoDeAnalise e a mesma guarda de pacote. Acrescentado na Etapa 12.
@RestController
@RequestMapping("/api/analises/{id}")
class ControladorDeCorrecoes {

    private final ServicoDeAnalise analises;
    private final CorrecoesDeAnalise correcoes;
    private final MontadorDaConferenciaExposta conferencias;
    private final LimitesDeUpload limites;
    private final Clock relogio;
    private final ServicoDoHistorico historico;

    // Construtor que recebe o serviço de análise, o registro de correções, o montador da resposta, os limites de envio e o relógio.
    ControladorDeCorrecoes(
            ServicoDeAnalise analises,
            CorrecoesDeAnalise correcoes,
            MontadorDaConferenciaExposta conferencias,
            LimitesDeUpload limites,
            Clock relogio,
            ServicoDoHistorico historico) {
        this.analises = analises;
        this.correcoes = correcoes;
        this.conferencias = conferencias;
        this.limites = limites;
        this.relogio = relogio;
        this.historico = historico;
    }

    // Representa os vínculos de correção de uma análise. Sem análise corrigida, vem null com o motivo.
    record VinculosExpostos(String corrige, String motivoSemCorrecao, List<String> corrigidaPor) {
    }

    // Recebe o arquivo corrigido, roda uma análise nova e registra que ela corrige a do endereço.
    @PostMapping("/correcoes")
    ResponseEntity<RespostaDaConferencia> corrigir(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado quem,
            @RequestParam(value = ControladorDeAnalises.CAMPO_DO_ARQUIVO, required = false) MultipartFile arquivo) {

        if (!correcoes.existeAnalise(id)) {
            throw new ExecucaoNaoEncontrada(id);
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new PedidoInvalido(
                    ("Nenhum arquivo foi enviado no campo \"%s\". A correção é uma análise nova do arquivo "
                            + "corrigido.").formatted(ControladorDeAnalises.CAMPO_DO_ARQUIVO));
        }

        try (InputStream conteudo = arquivo.getInputStream();
             AreaDaAnalise area = AreaDaAnalise.receber(arquivo.getOriginalFilename(), conteudo, limites)) {

            ResultadoDaAnalise resultado = analises.analisar(area.origem());
            correcoes.registrar(resultado.id(), id, quem.id(), relogio.instant());
            // Etapa 13: a correção também é uma análise executada por alguém.
            historico.registrarExecutor(resultado.id(), quem.id());
            return ResponseEntity.created(URI.create("/api/analises/" + resultado.id()))
                    .body(conferencias.resultado(resultado.id()));

        } catch (IOException naoLeuOEnvio) {
            throw new PacoteRecusado(
                    "Não foi possível ler o arquivo enviado até o fim. O envio pode ter sido "
                            + "interrompido; tente de novo.",
                    naoLeuOEnvio);
        }
    }

    // Devolve os vínculos de correção da análise.
    @GetMapping("/vinculos")
    VinculosExpostos vinculos(@PathVariable UUID id) {
        if (!correcoes.existeAnalise(id)) {
            throw new ExecucaoNaoEncontrada(id);
        }
        VinculosDaAnalise vinculos = correcoes.vinculos(id);
        return new VinculosExpostos(
                vinculos.corrige().map(UUID::toString).orElse(null),
                vinculos.corrige().isPresent() ? null : "esta análise não foi enviada como correção de outra",
                vinculos.corrigidaPor().stream().map(UUID::toString).toList());
    }
}
