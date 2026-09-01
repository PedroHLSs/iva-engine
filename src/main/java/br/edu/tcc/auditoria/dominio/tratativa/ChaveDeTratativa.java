package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;

/**
 * O que uma tratativa trata: um item concreto, sob uma regra, numa versão dessa
 * regra.
 *
 * <p><strong>A versão da regra faz parte da chave, de propósito.</strong> Uma
 * justificativa é dada contra um critério — "esta regra, nesta versão, aponta
 * isto, e eu discordo por tal razão". Quando o critério muda, a justificativa
 * deixa de responder à pergunta que está sendo feita. Então a tratativa não é
 * encontrada, e o apontamento reabre para nova leitura humana.</p>
 *
 * <p>O efeito é intencional e não deve ser "corrigido" fazendo a busca ignorar a
 * versão: isso faria uma decisão antiga silenciar um apontamento novo que ela
 * nunca examinou. A tratativa antiga não é apagada — continua no banco, presa à
 * versão em que foi dada.</p>
 *
 * <p>Pelo mesmo raciocínio, mudar o conteúdo do item muda
 * {@link HashDoItem} e também reabre o apontamento.</p>
 */
public record ChaveDeTratativa(HashDoItem hashDoItem, String regraId, String regraVersao) {

    public ChaveDeTratativa {
        if (hashDoItem == null) {
            throw new TratativaInvalida(
                    "A tratativa precisa dizer sobre qual item foi dada. Sem o resumo do item ela não "
                            + "sobrevive ao reprocessamento do lote.");
        }
        exigirTexto(regraId, "regraId");
        exigirTexto(regraVersao, "regraVersao");
    }

    /** Chave da tratativa que se aplicaria ao apontamento indicado. */
    public static ChaveDeTratativa de(HashDoItem hashDoItem, Achado achado) {
        if (achado == null) {
            throw new TratativaInvalida("Não há apontamento a tratar.");
        }
        return new ChaveDeTratativa(hashDoItem, achado.regraId(), achado.regraVersao());
    }

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new TratativaInvalida(
                    "O campo \"%s\" da chave de tratativa é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
