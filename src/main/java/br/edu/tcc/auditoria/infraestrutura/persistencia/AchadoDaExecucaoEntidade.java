package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// Representa o vínculo entre uma execução e um apontamento que ela produziu. Sem esta tabela, a planilha de uma execução antiga traria os apontamentos da mais recente.
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

    // Construtor vazio exigido pelo JPA.
    protected AchadoDaExecucaoEntidade() {
    }

    // Construtor que recebe a execução e o apontamento.
    AchadoDaExecucaoEntidade(UUID execucaoId, UUID achadoId) {
        this.execucaoId = execucaoId;
        this.achadoId = achadoId;
    }

    UUID achadoId() {
        return achadoId;
    }

    // Representa a chave composta, execução e apontamento, exigida pelo JPA.
    static class Chave implements Serializable {

        private UUID execucaoId;
        private UUID achadoId;

        // Construtor vazio exigido pelo JPA.
        Chave() {
        }

        // Construtor que recebe a execução e o apontamento.
        Chave(UUID execucaoId, UUID achadoId) {
            this.execucaoId = execucaoId;
            this.achadoId = achadoId;
        }

        // Compara duas chaves pela execução e pelo apontamento.
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

        // Calcula o hash pela execução e pelo apontamento.
        @Override
        public int hashCode() {
            return Objects.hash(execucaoId, achadoId);
        }
    }
}
