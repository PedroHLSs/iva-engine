package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

/**
 * Uma linha da aba de não avaliados: o item que a regra não conseguiu julgar, e
 * por quê.
 *
 * <p>Vale a mesma regra de identificação da aba de achados — pseudônimo da
 * chave, mais a numeração do emitente para localizar a nota.</p>
 */
public record LinhaNaoAvaliada(
        String documentoPseudonimizado,
        String modelo,
        String serie,
        String numero,
        int numeroItem,
        String regraId,
        String regraVersao,
        String motivo) {

    public LinhaNaoAvaliada {
        if (documentoPseudonimizado == null || documentoPseudonimizado.isBlank()) {
            throw new PapelDeTrabalhoInvalido("A linha de não avaliado precisa do documento.");
        }
        if (numeroItem < 1) {
            throw new PapelDeTrabalhoInvalido(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (regraId == null || regraId.isBlank() || regraVersao == null || regraVersao.isBlank()) {
            throw new PapelDeTrabalhoInvalido(
                    "A linha de não avaliado precisa da regra e da versão dela.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new PapelDeTrabalhoInvalido(
                    "A linha de não avaliado precisa do motivo: é a única coisa que ela tem a dizer.");
        }
    }
}
