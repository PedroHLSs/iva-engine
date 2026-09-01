package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.Optional;

/**
 * O endereço de uma avaliação: documento, item e regra.
 *
 * <p>É a chave pela qual o gabarito e a saída do motor se encontram. Os dois
 * lados produzem listas planas — uma escrita à mão, outra pelo motor — e sem uma
 * chave estruturada o cruzamento vira concatenação de texto, que é onde um item
 * 1 e um item 11 se confundem.</p>
 *
 * <p>Não é o mesmo que a chave de tratativa da Etapa 5. Aquela inclui o resumo
 * do conteúdo do item, para sobreviver ao reprocessamento; esta identifica
 * posição, porque quem rotulou à mão anotou documento e número de item, não
 * resumo criptográfico.</p>
 *
 * @param chaveAcesso documento
 * @param numeroItem  item dentro do documento, a partir de 1
 * @param regraId     regra que julga, ou julgaria, este item
 */
public record EnderecoDaAvaliacao(ChaveAcesso chaveAcesso, int numeroItem, String regraId) {

    public EnderecoDaAvaliacao {
        if (chaveAcesso == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O endereço da avaliação precisa da chave de acesso do documento.");
        }
        if (regraId == null || regraId.isBlank()) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O endereço da avaliação precisa do identificador da regra.");
        }
        if (numeroItem < 1) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        regraId = regraId.strip();
    }

    /**
     * O endereço de uma avaliação produzida pelo motor.
     *
     * <p>Vazio quando a avaliação é do documento inteiro, sem item. O conjunto
     * de regras atual só avalia itens, mas a modelagem admite a outra forma, e
     * responder um número de item inventado para ela seria afirmar posição que a
     * avaliação não declarou.</p>
     */
    public static Optional<EnderecoDaAvaliacao> de(Avaliacao avaliacao) {
        if (avaliacao == null) {
            throw new AvaliacaoDeAcuraciaInvalida("Não há avaliação cujo endereço obter.");
        }
        if (avaliacao.numeroItem().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EnderecoDaAvaliacao(
                avaliacao.chaveAcesso(), avaliacao.numeroItem().getAsInt(), avaliacao.regraId()));
    }
}
