package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

/**
 * O que uma regra concluiu sobre um produto, já no vocabulário da interface.
 *
 * <p>Guarda a regra junto do estado, e não só o estado: uma linha de tela que
 * diz "não foi possível concluir" sem dizer qual verificação não concluiu é uma
 * linha sem endereço, e relatório de auditoria não tem linha sem endereço.</p>
 *
 * <p>A versão vem junto pelo motivo da D006 — a tratativa e o apontamento são
 * identificados por regra <em>e</em> versão, e um resultado exibido hoje precisa
 * dizer contra qual critério foi produzido. Quando ela não está gravada, o que
 * vem junto é a explicação disso: ver {@link VersaoDaRegra}.</p>
 */
public record VerificacaoDoProduto(
        String regraId, VersaoDaRegra versao, EstadoDeConferencia estado) {

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

    /**
     * A verificação correspondente a uma avaliação recém-produzida pelo motor.
     *
     * <p>Aqui a versão sempre existe — a avaliação acabou de sair da regra, e
     * carrega a dela.</p>
     */
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
