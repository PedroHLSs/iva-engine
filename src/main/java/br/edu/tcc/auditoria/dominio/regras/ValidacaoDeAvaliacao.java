package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.excecao.AvaliacaoInvalida;

import java.util.OptionalInt;

// Verificações usadas pelos resultados que não têm apontamento. Fica fora da interface Avaliacao para não ficar visível às regras.
final class ValidacaoDeAvaliacao {

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private ValidacaoDeAvaliacao() {
    }

    // Confere se regra, versão, nota e item foram informados; na avaliação da nota inteira, o item vem vazio.
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

    // Método auxiliar que dá erro se um texto obrigatório vier vazio.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new AvaliacaoInvalida("O campo \"%s\" da avaliação é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
