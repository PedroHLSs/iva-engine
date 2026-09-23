package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Controlador que responde GET /api/execucoes/{id}/nao-avaliados: os itens que o motor não conseguiu avaliar, com o motivo. Tem endereço próprio para não avaliado nunca parecer lote limpo.
@RestController
@RequestMapping("/api/execucoes/{id}/nao-avaliados")
class ControladorDeNaoAvaliados {

    private final MontadorDeRespostas respostas;

    // Construtor que recebe o montador de respostas.
    ControladorDeNaoAvaliados(MontadorDeRespostas respostas) {
        this.respostas = respostas;
    }

    // Lista uma página dos não avaliados, podendo filtrar por regra.
    @GetMapping
    RespostaDeNaoAvaliados listar(
            @PathVariable UUID id,
            @RequestParam(required = false) String regra,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + FiltroDeAchados.LIMITE_PADRAO) int tamanho) {

        return respostas.naoAvaliados(
                id,
                Parametros.textoOpcional(regra, "regra"),
                Parametros.pagina(pagina),
                Parametros.tamanho(tamanho));
    }
}
