package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Os itens que o motor não conseguiu julgar naquela execução, com o motivo.
 *
 * <h2>Endpoint próprio, e não um detalhe de outro</h2>
 *
 * <p>Porque não avaliar não é um caso especial de não apontar. Se estas linhas
 * fossem um campo dentro da resposta de achados, quem consome escolheria não
 * olhar — e um lote em que nada pôde ser avaliado sairia com cara de lote limpo,
 * que é o defeito que a D007 descreve para a planilha e que valeria igual aqui.</p>
 */
@RestController
@RequestMapping("/api/execucoes/{id}/nao-avaliados")
class ControladorDeNaoAvaliados {

    private final MontadorDeRespostas respostas;

    ControladorDeNaoAvaliados(MontadorDeRespostas respostas) {
        this.respostas = respostas;
    }

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
