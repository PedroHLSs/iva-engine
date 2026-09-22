package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
// Representa as métricas de acurácia de uma regra, incluindo o identificador da regra e a contagem de acertos e erros.
public record MetricasDaRegra(String regraId, ContagemDeAcuracia contagem) {

    public MetricasDaRegra {
        if (regraId == null || regraId.isBlank()) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A linha de métricas precisa do identificador da regra a que se refere.");
        }
        if (contagem == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A linha de métricas precisa da contagem. Regra não medida tem contagem zerada, "
                            + "nunca contagem ausente.");
        }
        regraId = regraId.strip();
    }
}
