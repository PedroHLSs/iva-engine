package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;

// Representa a chave de uma tratativa: o item, a regra e a versão da regra. A versão entra de propósito: se a regra muda, o apontamento reabre. Não mudar a busca para ignorar a versão.
public record ChaveDeTratativa(HashDoItem hashDoItem, String regraId, String regraVersao) {

    // Valida que a chave tenha o resumo do item, a regra e a versão.
    public ChaveDeTratativa {
        if (hashDoItem == null) {
            throw new TratativaInvalida(
                    "A tratativa precisa dizer sobre qual item foi dada. Sem o resumo do item ela não "
                            + "sobrevive ao reprocessamento do lote.");
        }
        exigirTexto(regraId, "regraId");
        exigirTexto(regraVersao, "regraVersao");
    }

    // Método estático que monta a chave da tratativa de um apontamento, junto com o resumo do item.
    public static ChaveDeTratativa de(HashDoItem hashDoItem, Achado achado) {
        if (achado == null) {
            throw new TratativaInvalida("Não há apontamento a tratar.");
        }
        return new ChaveDeTratativa(hashDoItem, achado.regraId(), achado.regraVersao());
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new TratativaInvalida(
                    "O campo \"%s\" da chave de tratativa é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
