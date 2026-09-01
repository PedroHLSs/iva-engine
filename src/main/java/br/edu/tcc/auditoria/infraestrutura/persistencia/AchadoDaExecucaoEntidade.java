package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo entre uma execução e um apontamento que ela produziu.
 *
 * <p>A linha do apontamento guarda só a primeira e a última execução que o
 * viram. Sem esta tabela, o papel de trabalho de uma execução antiga traria os
 * apontamentos errados — os que a execução mais recente viu — e as contagens do
 * Resumo não bateriam com as linhas da aba de achados.</p>
 */
@Entity
@Table(name = "achado_da_execucao")
@IdClass(AchadoDaExecucaoEntidade.Chave.class)
class AchadoDaExecucaoEntidade {

    @Id
    @Column(name = "execucao_id", nullable = false)
    private UUID execucaoId;

    @Id
    @Column(name = "achado_id", nullable = false)
    private UUID achadoId;

    protected AchadoDaExecucaoEntidade() {
        // Exigido pelo JPA.
    }

    AchadoDaExecucaoEntidade(UUID execucaoId, UUID achadoId) {
        this.execucaoId = execucaoId;
        this.achadoId = achadoId;
    }

    UUID achadoId() {
        return achadoId;
    }

    /** Chave composta exigida pelo JPA. */
    static class Chave implements Serializable {

        private UUID execucaoId;
        private UUID achadoId;

        Chave() {
        }

        Chave(UUID execucaoId, UUID achadoId) {
            this.execucaoId = execucaoId;
            this.achadoId = achadoId;
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(execucaoId, chave.execucaoId)
                    && Objects.equals(achadoId, chave.achadoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(execucaoId, achadoId);
        }
    }
}
