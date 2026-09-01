package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;

/**
 * O desempenho medido de uma regra.
 *
 * <p>Existe uma linha destas para <strong>toda</strong> regra do conjunto,
 * inclusive as que o gabarito não cita. Omitir a regra não medida faria o
 * relatório parecer completo quando não é — e a linha zerada, com métricas
 * indefinidas, diz exatamente o que aconteceu: ninguém rotulou nada para ela.</p>
 *
 * @param regraId  identificador da regra, como o conjunto o declara
 * @param contagem a matriz de confusão dela, mais não avaliados e sem avaliação
 */
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
