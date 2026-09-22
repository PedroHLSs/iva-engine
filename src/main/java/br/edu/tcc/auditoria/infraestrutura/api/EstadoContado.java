package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ContagemDeEstados;
import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.util.ArrayList;
import java.util.List;

/**
 * Um dos quatro estados e quantos há dele.
 *
 * <p>Vai como lista, e não como mapa de código para número, por dois motivos. O
 * primeiro é a ordem: a lista sai na precedência declarada e não depende de o
 * cliente reordenar chaves. O segundo é que <strong>o rótulo e a explicação
 * viajam junto do número</strong> — um código como
 * {@code NAO_FOI_POSSIVEL_CONCLUIR} solto numa resposta convida quem consome a
 * inventar a frase que vai na tela, e a frase é conteúdo desta etapa, não
 * enfeite.</p>
 */
public record EstadoContado(String estado, String rotulo, String explicacao, int quantidade) {

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

    /** Os quatro, sempre os quatro, na ordem de precedência e inclusive os zeros. */
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
