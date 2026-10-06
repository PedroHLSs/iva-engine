package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;

// Representa a tolerância de valor da R05 que uma execução usou, com a origem dela (D023). Duas execuções com tolerâncias diferentes dão resultados diferentes na R05, e é este registro que deixa isso visível sem investigar.
public record ToleranciaDaExecucao(ToleranciaDeValor valor, OrigemDaTolerancia origem) {

    // Motivo escrito quando a execução não registrou a tolerância.
    public static final String NAO_REGISTRADA =
            "não registrada: a execução foi gravada antes de 04/10/2026, quando a tolerância usada na R05 "
                    + "não era guardada; não há como saber qual valor valeu";

    // Valida que haja valor e origem.
    public ToleranciaDaExecucao {
        if (valor == null || origem == null) {
            throw new AuditoriaInvalida("A tolerância da execução precisa do valor e da origem.");
        }
    }

    // Retorna a quantia como texto, sem notação científica e com as casas decimais que vieram.
    public String quantia() {
        return valor.quantia().toPlainString();
    }

    // Retorna a tolerância por extenso, com a origem: "0.01 (padrão do sistema; ...)".
    public String texto() {
        return "%s (%s)".formatted(quantia(), origem.rotulo());
    }
}
