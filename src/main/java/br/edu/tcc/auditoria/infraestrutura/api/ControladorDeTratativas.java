package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.tratativa.RegistroDeTratativa;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativaAtribuida;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.infraestrutura.seguranca.UsuarioAutenticado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Controlador de /api/achados/{id}/tratativas: registrar a decisão sobre um apontamento e ler o histórico de decisões. O autor é sempre quem está logado, nunca um campo do pedido. Registrar é para fiscal e administrador; consulta só lê. Não há PUT nem DELETE: tratar de novo acrescenta uma decisão, e nenhuma é apagada. Acrescentado na Etapa 12.
@RestController
@RequestMapping("/api/achados/{id}/tratativas")
class ControladorDeTratativas {

    private final ServicoDeTratativaAtribuida tratativas;
    private final PoliticaDeExposicao politica;

    // Construtor que recebe o serviço de tratativa e a política de exposição da justificativa.
    ControladorDeTratativas(ServicoDeTratativaAtribuida tratativas, PoliticaDeExposicao politica) {
        this.tratativas = tratativas;
        this.politica = politica;
    }

    // Representa o pedido de tratativa: a decisão e a justificativa. Quem decidiu não vem no pedido.
    record PedidoDeTratativa(String decisao, String justificativa) {
    }

    // Registra a decisão em nome de quem está logado.
    @PostMapping
    ResponseEntity<RegistroDeTratativaExposto> registrar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado quem,
            @RequestBody(required = false) PedidoDeTratativa pedido) {
        if (pedido == null) {
            throw new PedidoInvalido("Informe a decisão e a justificativa.");
        }
        DecisaoDeTratativa decisao = Parametros.constante(
                pedido.decisao(), DecisaoDeTratativa.class, "decisao", null);
        RegistroDeTratativa registro = tratativas.registrar(id, decisao, pedido.justificativa(), quem.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistroDeTratativaExposto.de(registro, politica));
    }

    // Devolve o histórico de decisões do apontamento, da mais antiga à mais recente.
    @GetMapping
    List<RegistroDeTratativaExposto> historico(@PathVariable UUID id) {
        return tratativas.historicoDo(id).stream()
                .map(registro -> RegistroDeTratativaExposto.de(registro, politica))
                .toList();
    }
}
