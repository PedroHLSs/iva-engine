package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Um apontamento como ele aparece para quem consulta o relatório: com o
 * identificador pelo qual pode ser tratado, quando foi visto, e a tratativa que
 * houver.
 *
 * <h2>O apontamento já vem tratado</h2>
 *
 * <p>{@code tratativa} preenchida significa que alguém já decidiu sobre este
 * apontamento e a decisão continua valendo. Vazia significa que o apontamento
 * está aberto — inclusive quando existe uma tratativa dada em <em>outra</em>
 * versão da mesma regra: aquela decisão respondeu a outro critério e não é
 * trazida para cá. Ver
 * {@link br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa}.</p>
 *
 * <p>Apontamento tratado não desaparece da listagem. Ele continua no relatório,
 * agora acompanhado do que se concluiu sobre ele: uma auditoria que esconde o
 * que foi levantado deixa de ser conferível.</p>
 *
 * @param id           identificador da linha gravada, usado para tratar o apontamento
 * @param detectadoEm  quando o apontamento apareceu pela primeira vez
 * @param vistoEm      quando o apontamento foi gerado pela última vez
 */
public record AchadoRegistrado(
        UUID id,
        Achado achado,
        HashDoItem hashDoItem,
        Optional<Tratativa> tratativa,
        Instant detectadoEm,
        Instant vistoEm) {

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

    /** Indica se já há decisão humana registrada para este apontamento. */
    public boolean tratado() {
        return tratativa.isPresent();
    }

    /** Indica se o apontamento continua aberto, isto é, sem tratativa aplicável. */
    public boolean aberto() {
        return tratativa.isEmpty();
    }
}
