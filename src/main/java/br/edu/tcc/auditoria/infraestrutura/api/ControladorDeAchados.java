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

// Controlador que responde GET /api/execucoes/{id}/achados: os apontamentos de uma execução, em páginas e com filtros. O total antes do filtro e o filtro aplicado voltam na resposta, para lista curta não parecer acervo limpo.
@RestController
@RequestMapping("/api/execucoes/{id}/achados")
class ControladorDeAchados {

    private final MontadorDeRespostas respostas;

    // Construtor que recebe o montador de respostas.
    ControladorDeAchados(MontadorDeRespostas respostas) {
        this.respostas = respostas;
    }

    // Lista uma página dos achados, podendo filtrar por regra, gravidade e situação da tratativa.
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

    // Método auxiliar que converte o texto em gravidade, sem ligar para maiúscula ou minúscula; recusa valor desconhecido.
    private static Severidade severidade(String informada) {
        return Arrays.stream(Severidade.values())
                .filter(severidade -> severidade.name().equalsIgnoreCase(informada))
                .findFirst()
                .orElseThrow(() -> new PedidoInvalido(
                        "Severidade desconhecida: \"%s\". Valores aceitos: %s.".formatted(
                                informada, nomesDe(Severidade.values()))));
    }

    // Método auxiliar que converte o texto em status de tratativa, sem ligar para maiúscula ou minúscula; recusa valor desconhecido.
    private static StatusDeTratativa status(String informado) {
        return Arrays.stream(StatusDeTratativa.values())
                .filter(status -> status.name().equalsIgnoreCase(informado))
                .findFirst()
                .orElseThrow(() -> new PedidoInvalido(
                        "Status de tratativa desconhecido: \"%s\". Valores aceitos: %s.".formatted(
                                informado, nomesDe(StatusDeTratativa.values()))));
    }

    // Método auxiliar que junta os nomes aceitos, para a mensagem de erro.
    private static String nomesDe(Enum<?>[] valores) {
        return String.join(", ", Arrays.stream(valores).map(Enum::name).toList());
    }
}
