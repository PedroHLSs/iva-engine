package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.time.LocalDate;
import java.util.Optional;

/**
 * De onde veio, e para quando vale, cada linha do catálogo que a tela mostra.
 *
 * <p>Nenhum destes três campos é escrito pelo sistema. Todos vêm da carga: a
 * fonte é o texto que quem importou declarou, e as datas são as da vigência
 * registrada. A tela os repete; não os interpreta, não os abrevia e não os
 * completa.</p>
 *
 * <p>Fim vazio é vigência aberta, e a tela escreve isso por extenso. Escrever
 * uma data de fim inventada, ou deixar o campo em branco, seriam os dois jeitos
 * de mentir sobre o mesmo dado.</p>
 */
public record ReferenciaNormativa(
        String fonteNormativa, LocalDate vigenciaInicio, Optional<LocalDate> vigenciaFim) {

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

    /** A procedência do registro do catálogo, como ela foi importada. */
    public static ReferenciaNormativa de(ProcedenciaNormativa procedencia) {
        if (procedencia == null) {
            throw new ConferenciaInvalida("Não há procedência normativa a apresentar.");
        }
        return new ReferenciaNormativa(
                procedencia.fonteNormativa(), procedencia.vigenciaInicio(), procedencia.vigenciaFim());
    }

    /** Se a vigência segue aberta, sem último dia declarado na carga. */
    public boolean vigenciaAberta() {
        return vigenciaFim.isEmpty();
    }
}
