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
 * A procedência que uma carga declarou para uma tabela.
 *
 * <p>É o que permite à tela avisar que está exibindo dado de demonstração sem
 * depender de ninguém ter lembrado de ligar uma propriedade. Ausência de linha
 * não é lida como "normativo": é carga anterior à declaração, ou tabela sem
 * registro — ver a V9 e {@code SituacaoDaNatureza}.</p>
 */
@Entity
@Table(name = "natureza_da_carga")
@IdClass(NaturezaDaCargaEntidade.Chave.class)
class NaturezaDaCargaEntidade {

    @Id
    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Id
    @Column(name = "tabela", nullable = false)
    private String tabela;

    @Column(name = "natureza", nullable = false)
    private String natureza;

    protected NaturezaDaCargaEntidade() {
        // Exigido pelo JPA.
    }

    NaturezaDaCargaEntidade(UUID cargaId, String tabela, String natureza) {
        this.cargaId = cargaId;
        this.tabela = tabela;
        this.natureza = natureza;
    }

    String tabela() {
        return tabela;
    }

    String natureza() {
        return natureza;
    }

    /** Chave composta, no mesmo desenho de {@code CoberturaCatalogoEntidade}. */
    static class Chave implements Serializable {

        private UUID cargaId;
        private String tabela;

        protected Chave() {
            // Exigido pelo JPA.
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Chave chave)) {
                return false;
            }
            return Objects.equals(cargaId, chave.cargaId) && Objects.equals(tabela, chave.tabela);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cargaId, tabela);
        }
    }
}
