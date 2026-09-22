package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.StatusDeTratativa;
import br.edu.tcc.auditoria.dominio.Severidade;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.UUID;

/**
 * Os apontamentos de uma execução, paginados e filtráveis.
 *
 * <p>Filtrar não é esconder: o total antes do recorte vai na resposta, em
 * {@link PaginaExposta}, justamente para que uma listagem curta não pareça um
 * acervo limpo. E o filtro aplicado volta escrito, para que ninguém confunda
 * "nada atende" com "ninguém procurou".</p>
 */
@RestController
@RequestMapping("/api/execucoes/{id}/achados")
class ControladorDeAchados {

    private final MontadorDeRespostas respostas;

    ControladorDeAchados(MontadorDeRespostas respostas) {
        this.respostas = respostas;
    }

    @GetMapping
    RespostaDeAchados listar(
            @PathVariable UUID id,
            @RequestParam(required = false) String regra,
            @RequestParam(required = false) String severidade,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + FiltroDeAchados.LIMITE_PADRAO) int tamanho) {

        return respostas.achados(
                id,
                Parametros.textoOpcional(regra, "regra"),
                Parametros.textoOpcional(severidade, "severidade").map(ControladorDeAchados::severidade),
                Parametros.textoOpcional(status, "status").map(ControladorDeAchados::status),
                Parametros.pagina(pagina),
                Parametros.tamanho(tamanho));
    }

    private static Severidade severidade(String informada) {
        return Arrays.stream(Severidade.values())
                .filter(severidade -> severidade.name().equalsIgnoreCase(informada))
                .findFirst()
                .orElseThrow(() -> new PedidoInvalido(
                        "Severidade desconhecida: \"%s\". Valores aceitos: %s.".formatted(
                                informada, nomesDe(Severidade.values()))));
    }

    private static StatusDeTratativa status(String informado) {
        return Arrays.stream(StatusDeTratativa.values())
                .filter(status -> status.name().equalsIgnoreCase(informado))
                .findFirst()
                .orElseThrow(() -> new PedidoInvalido(
                        "Status de tratativa desconhecido: \"%s\". Valores aceitos: %s.".formatted(
                                informado, nomesDe(StatusDeTratativa.values()))));
    }

    private static String nomesDe(Enum<?>[] valores) {
        return String.join(", ", Arrays.stream(valores).map(Enum::name).toList());
    }
}
