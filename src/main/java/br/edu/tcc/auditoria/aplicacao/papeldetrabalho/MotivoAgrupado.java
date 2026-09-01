package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

/**
 * Um motivo de não avaliação e quantas vezes ele apareceu, numa regra.
 *
 * <p>Agrupado por regra e por texto do motivo. É o que transforma "quatro mil
 * não avaliadas" em algo acionável: quase sempre são poucos motivos distintos
 * repetidos muitas vezes, e cada um aponta para um dono — falta de campo no
 * documento é problema de quem emite, falta de tabela no catálogo é de quem
 * importa.</p>
 */
public record MotivoAgrupado(String regraId, String motivo, int quantidade) {

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
