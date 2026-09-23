package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

// Representa um documento junto com seus itens, garantindo que o documento não seja nulo e que a lista de itens seja válida.
public record DocumentoComItens(Documento documento, List<ItemDocumento> itens) {

    public DocumentoComItens {
        if (documento == null) {
            throw new AuditoriaInvalida("Não há documento a auditar.");
        }
        if (itens == null) {
            throw new AuditoriaInvalida(
                    "A lista de itens deve ser vazia quando o documento não tem item, nunca nula.");
        }
        if (itens.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de itens não pode conter elemento nulo.");
        }
        itens = List.copyOf(itens);
    }

// Retorna a lista de itens ordenada pelo número do item, garantindo que a ordem seja consistente para processamento posterior.
    public List<ItemDocumento> itensOrdenados() {
        return itens.stream()
                .sorted(Comparator.comparingInt(ItemDocumento::numeroItem))
                .toList();
    }
}
