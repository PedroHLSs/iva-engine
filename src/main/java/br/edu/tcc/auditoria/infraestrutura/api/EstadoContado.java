package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ContagemDeEstados;
import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.util.ArrayList;
import java.util.List;

// Representa um dos quatro estados com a quantidade dele. O rótulo e a explicação vão junto do número, para a tela não ter de inventar a frase.
public record EstadoContado(String estado, String rotulo, String explicacao, int quantidade) {

    // Valida que o estado tenha código, rótulo e explicação, e que a quantidade não seja negativa.
    public EstadoContado {
        if (estado == null || estado.isBlank()) {
            throw new RespostaInvalida("A contagem precisa dizer de que estado ela é.");
        }
        if (rotulo == null || rotulo.isBlank() || explicacao == null || explicacao.isBlank()) {
            throw new RespostaInvalida(
                    ("O estado %s precisa sair com o rótulo e a explicação dele: código sozinho faz "
                            + "quem consome escrever a frase por conta própria.").formatted(estado));
        }
        if (quantidade < 0) {
            throw new RespostaInvalida("Contagem de estado não pode ser negativa.");
        }
    }

    // Método estático que devolve os quatro estados, sempre os quatro, na ordem de precedência e com os zeros.
    static List<EstadoContado> de(ContagemDeEstados contagem) {
        List<EstadoContado> contados = new ArrayList<>();
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            contados.add(new EstadoContado(
                    estado.name(),
                    estado.rotulo(),
                    estado.explicacao(),
                    contagem.quantidadeDe(estado)));
        }
        return List.copyOf(contados);
    }
}
