package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Severidade;

import java.util.Optional;

// Representa o recorte de uma listagem de apontamentos: todos os critérios são opcionais, e sem nenhum a listagem traz tudo, até o limite.
public record FiltroDeAchados(
        Optional<Severidade> severidade,
        Optional<String> regraId,
        Optional<ChaveAcesso> chaveAcesso,
        boolean apenasAbertos,
        int limite) {

    // Limite usado quando quem consulta não informa um.
    public static final int LIMITE_PADRAO = 50;

    // Valida que os critérios venham em Optional, que o filtro por regra não venha vazio e que o limite seja ao menos 1.
    public FiltroDeAchados {
        exigirOptional(severidade, "severidade");
        exigirOptional(regraId, "regraId");
        exigirOptional(chaveAcesso, "chaveAcesso");
        regraId.ifPresent(identificador -> {
            if (identificador.isBlank()) {
                throw new ConsultaInvalida(
                        "O filtro por regra veio vazio. Para não filtrar por regra, use Optional.empty().");
            }
        });
        if (limite < 1) {
            throw new ConsultaInvalida(
                    "O limite da listagem deve ser maior ou igual a 1, mas veio %d.".formatted(limite));
        }
    }

    // Método estático que cria um filtro sem nenhum critério, com o limite padrão.
    public static FiltroDeAchados tudo() {
        return new FiltroDeAchados(
                Optional.empty(), Optional.empty(), Optional.empty(), false, LIMITE_PADRAO);
    }

    // Método auxiliar para verificar se o critério é nulo e lançar uma exceção.
    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new ConsultaInvalida(
                    "O critério \"%s\" deve ser Optional.empty() quando não informado, nunca nulo."
                            .formatted(nomeDoCampo));
        }
    }
}
