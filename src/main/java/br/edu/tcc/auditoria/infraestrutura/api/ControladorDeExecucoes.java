package br.edu.tcc.auditoria.infraestrutura.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * As execuções gravadas: a listagem e o detalhe de uma.
 *
 * <h2>Só GET</h2>
 *
 * <p>Não há endpoint que dispare auditoria, importe catálogo ou registre
 * tratativa, e isso não é uma etapa faltando: é a decisão D009. Auditar tem
 * efeito colateral gravado e demora minutos; importar catálogo decide o que o
 * sistema vai afirmar sobre a norma; tratar achado é ato de uma pessoa
 * identificada. Nenhum dos três cabe atrás de uma porta sem autenticação, e a
 * autenticação está fora do escopo desta etapa. Quem escreve é a CLI.</p>
 */
@RestController
@RequestMapping("/api/execucoes")
class ControladorDeExecucoes {

    private final MontadorDeRespostas respostas;

    ControladorDeExecucoes(MontadorDeRespostas respostas) {
        this.respostas = respostas;
    }

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

    @GetMapping("/{id}")
    RespostaDaExecucao detalhar(@PathVariable UUID id) {
        return respostas.execucao(id);
    }
}
