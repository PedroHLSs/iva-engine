package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

// Representa o que uma regra concluiu sobre um produto, com a regra, a versão e o estado já no vocabulário da interface.
public record VerificacaoDoProduto(
        String regraId, VersaoDaRegra versao, EstadoDeConferencia estado) {

    // Valida que a verificação tenha regra, versão e estado.
    public VerificacaoDoProduto {
        if (regraId == null || regraId.isBlank()) {
            throw new ConferenciaInvalida("O campo \"regraId\" da verificação é obrigatório.");
        }
        if (versao == null) {
            throw new ConferenciaInvalida(
                    ("A verificação da regra %s precisa dizer a versão dela, ou por que não se sabe. "
                            + "Use VersaoDaRegra.naoRegistrada(motivo).").formatted(regraId));
        }
        if (estado == null) {
            throw new ConferenciaInvalida(
                    "A verificação da regra %s precisa do estado: é o que ela tem a dizer."
                            .formatted(regraId));
        }
    }

    // Método estático que cria a verificação a partir de uma avaliação recém-produzida pelo motor.
    public static VerificacaoDoProduto de(Avaliacao avaliacao) {
        if (avaliacao == null) {
            throw new ConferenciaInvalida("Não há avaliação de onde tirar a verificação do produto.");
        }
        return new VerificacaoDoProduto(
                avaliacao.regraId(),
                VersaoDaRegra.registrada(avaliacao.regraVersao()),
                TraducaoDeDesfecho.de(avaliacao));
    }
}
