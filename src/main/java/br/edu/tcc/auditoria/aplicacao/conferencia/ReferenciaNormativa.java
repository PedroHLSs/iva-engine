package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.time.LocalDate;
import java.util.Optional;

// Representa de onde veio e para quando vale cada linha do catálogo que a tela mostra; fim vazio é vigência aberta.
public record ReferenciaNormativa(
        String fonteNormativa, LocalDate vigenciaInicio, Optional<LocalDate> vigenciaFim) {

    // Valida que a referência tenha fonte e início de vigência, com o fim em Optional.
    public ReferenciaNormativa {
        if (fonteNormativa == null || fonteNormativa.isBlank()) {
            throw new ConferenciaInvalida(
                    "A referência normativa precisa da fonte: sem ela a tela afirmaria tratamento sem "
                            + "dizer de onde tirou.");
        }
        if (vigenciaInicio == null) {
            throw new ConferenciaInvalida("A referência normativa precisa do início da vigência.");
        }
        if (vigenciaFim == null) {
            throw new ConferenciaInvalida(
                    "Vigência aberta se representa com Optional.empty(), nunca com nulo.");
        }
    }

    // Método estático que cria a referência a partir da procedência do registro do catálogo.
    public static ReferenciaNormativa de(ProcedenciaNormativa procedencia) {
        if (procedencia == null) {
            throw new ConferenciaInvalida("Não há procedência normativa a apresentar.");
        }
        return new ReferenciaNormativa(
                procedencia.fonteNormativa(), procedencia.vigenciaInicio(), procedencia.vigenciaFim());
    }

    // Indica se a vigência segue aberta, sem último dia declarado na carga.
    public boolean vigenciaAberta() {
        return vigenciaFim.isEmpty();
    }
}
