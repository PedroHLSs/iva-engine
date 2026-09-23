package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

// Representa uma linha da aba de não avaliados: o item que a regra não conseguiu julgar e o motivo, com o pseudônimo da chave.
public record LinhaNaoAvaliada(
        String documentoPseudonimizado,
        String modelo,
        String serie,
        String numero,
        int numeroItem,
        String regraId,
        String regraVersao,
        String motivo) {

    // Valida que a linha tenha documento, número do item, regra, versão e motivo.
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
