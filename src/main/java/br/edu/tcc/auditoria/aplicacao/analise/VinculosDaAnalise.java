package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Representa os vínculos de correção de uma análise: qual análise ela corrige, se corrige alguma, e quais análises a corrigiram depois.
public record VinculosDaAnalise(Optional<UUID> corrige, List<UUID> corrigidaPor) {

    // Valida que os campos não venham nulos e guarda cópia da lista.
    public VinculosDaAnalise {
        if (corrige == null || corrigidaPor == null) {
            throw new AnaliseInvalida("Vínculo ausente se representa com vazio, nunca com nulo.");
        }
        corrigidaPor = List.copyOf(corrigidaPor);
    }
}
