package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Representa um apontamento gravado como aparece na consulta, com identificador, datas e a tratativa que houver; apontamento tratado continua na listagem.
public record AchadoRegistrado(
        UUID id,
        Achado achado,
        HashDoItem hashDoItem,
        Optional<Tratativa> tratativa,
        Instant detectadoEm,
        Instant vistoEm) {

    // Valida que o apontamento tenha identificador, resumo do item, datas e tratativa em Optional.
    public AchadoRegistrado {
        if (id == null) {
            throw new ConsultaInvalida("O apontamento gravado precisa de identificador.");
        }
        if (achado == null) {
            throw new ConsultaInvalida("Não há apontamento a apresentar.");
        }
        if (hashDoItem == null) {
            throw new ConsultaInvalida("O apontamento gravado precisa do resumo do item apontado.");
        }
        if (tratativa == null) {
            throw new ConsultaInvalida(
                    "Apontamento sem tratativa se representa com Optional.empty(), nunca com nulo.");
        }
        if (detectadoEm == null || vistoEm == null) {
            throw new ConsultaInvalida(
                    "O apontamento gravado precisa dizer quando foi detectado e quando foi visto pela "
                            + "última vez.");
        }
    }

    // Indica se já há decisão humana registrada para este apontamento.
    public boolean tratado() {
        return tratativa.isPresent();
    }

    // Indica se o apontamento continua aberto, isto é, sem tratativa aplicável.
    public boolean aberto() {
        return tratativa.isEmpty();
    }
}
