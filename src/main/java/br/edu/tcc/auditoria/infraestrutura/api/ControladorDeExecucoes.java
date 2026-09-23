package br.edu.tcc.auditoria.infraestrutura.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Controlador que responde, só por GET, a lista das execuções gravadas e o detalhe de uma. Importar catálogo e tratar achado continuam só na linha de comando.
@RestController
@RequestMapping("/api/execucoes")
class ControladorDeExecucoes {

    private final MontadorDeRespostas respostas;

    // Construtor que recebe o montador de respostas.
    ControladorDeExecucoes(MontadorDeRespostas respostas) {
        this.respostas = respostas;
    }

    // Lista as execuções mais recentes; recusa limite menor que 1 ou maior que o máximo.
    @GetMapping
    RespostaDeExecucoes listar(
            @RequestParam(defaultValue = "" + RespostaDeExecucoes.LIMITE_PADRAO) int limite) {

        if (limite < 1) {
            throw new PedidoInvalido(
                    "O limite deve ser maior ou igual a 1, mas veio %d.".formatted(limite));
        }
        if (limite > RespostaDeExecucoes.LIMITE_MAXIMO) {
            throw new PedidoInvalido(
                    "O limite máximo é %d, mas veio %d.".formatted(
                            RespostaDeExecucoes.LIMITE_MAXIMO, limite));
        }
        return respostas.execucoes(limite);
    }

    // Devolve o detalhe de uma execução pelo identificador.
    @GetMapping("/{id}")
    RespostaDaExecucao detalhar(@PathVariable UUID id) {
        return respostas.execucao(id);
    }
}
