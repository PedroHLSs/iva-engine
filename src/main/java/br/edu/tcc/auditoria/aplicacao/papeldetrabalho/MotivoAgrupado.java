package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

// Representa um motivo de não avaliação e quantas vezes ele apareceu numa regra, para mostrar a quem cabe resolver.
public record MotivoAgrupado(String regraId, String motivo, int quantidade) {

    // Valida que o motivo agrupado tenha regra, texto e quantidade de ao menos 1.
    public MotivoAgrupado {
        if (regraId == null || regraId.isBlank()) {
            throw new PapelDeTrabalhoInvalido("O motivo agrupado precisa dizer de que regra veio.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new PapelDeTrabalhoInvalido("O motivo agrupado precisa do texto do motivo.");
        }
        if (quantidade < 1) {
            throw new PapelDeTrabalhoInvalido(
                    "Um motivo agrupado com quantidade %d não deveria estar na lista."
                            .formatted(quantidade));
        }
    }
}
