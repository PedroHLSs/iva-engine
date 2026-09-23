package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// Representa a procedência que uma carga declarou para uma tabela, para a tela avisar quando mostra dado de demonstração. Falta de linha não quer dizer normativo: é carga anterior à declaração ou tabela sem registro.
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

    // Construtor vazio exigido pelo JPA.
    protected NaturezaDaCargaEntidade() {
    }

    // Construtor que recebe a carga, a tabela e a natureza.
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

    // Representa a chave composta, carga e tabela, no mesmo desenho da CoberturaCatalogoEntidade.
    static class Chave implements Serializable {

        private UUID cargaId;
        private String tabela;

        // Construtor vazio exigido pelo JPA.
        protected Chave() {
        }

        // Compara duas chaves pela carga e pela tabela.
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

        // Calcula o hash pela carga e pela tabela.
        @Override
        public int hashCode() {
            return Objects.hash(cargaId, tabela);
        }
    }
}
