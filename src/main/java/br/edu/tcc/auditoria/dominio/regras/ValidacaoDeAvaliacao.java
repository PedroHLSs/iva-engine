package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.excecao.AvaliacaoInvalida;

import java.util.OptionalInt;

/**
 * Verificações comuns às variantes de {@link Avaliacao} que não têm um
 * {@link Achado} de onde herdar a identificação.
 *
 * <p>Fica fora da interface de propósito: todo membro declarado dentro de uma
 * interface é público, e estas verificações são detalhe de construção, não parte
 * do contrato que as regras enxergam.</p>
 */
final class ValidacaoDeAvaliacao {

    private ValidacaoDeAvaliacao() {
    }

    static void exigirIdentificacao(
            String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem) {

        exigirTexto(regraId, "regraId");
        exigirTexto(regraVersao, "regraVersao");
        if (chaveAcesso == null) {
            throw new AvaliacaoInvalida("Toda avaliação precisa dizer sobre qual documento ela é.");
        }
        if (numeroItem == null) {
            throw new AvaliacaoInvalida(
                    "Avaliação de documento, sem item, se representa com OptionalInt.empty(), nunca com nulo.");
        }
        if (numeroItem.isPresent() && numeroItem.getAsInt() < 1) {
            throw new AvaliacaoInvalida(
                    "O número do item avaliado deve ser maior ou igual a 1, mas veio %d."
                            .formatted(numeroItem.getAsInt()));
        }
    }

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new AvaliacaoInvalida("O campo \"%s\" da avaliação é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
