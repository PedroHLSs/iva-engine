package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A cobertura que uma carga declarou para uma tabela normativa.
 *
 * <p>É o que separa "o catálogo foi carregado e não traz este registro" de "esta
 * tabela não foi carregada para esta data". Sem a linha correspondente, a regra
 * que depende da tabela responde não avaliado em vez de apontar.</p>
 */
@Entity
@Table(name = "cobertura_catalogo")
@IdClass(CoberturaCatalogoEntidade.Chave.class)
class CoberturaCatalogoEntidade {

    @Id
    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Id
    @Column(name = "tabela", nullable = false)
    private String tabela;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    /** Nulo é vigência aberta, e não uma data distante escolhida a esmo. */
    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    protected CoberturaCatalogoEntidade() {
        // Exigido pelo JPA.
    }

    CoberturaCatalogoEntidade(
            UUID cargaId,
            String tabela,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonteNormativa) {
        this.cargaId = cargaId;
        this.tabela = tabela;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
    }

    UUID cargaId() {
        return cargaId;
    }

    String tabela() {
        return tabela;
    }

    LocalDate vigenciaInicio() {
        return vigenciaInicio;
    }

    LocalDate vigenciaFim() {
        return vigenciaFim;
    }

    String fonteNormativa() {
        return fonteNormativa;
    }

    /** Chave composta exigida pelo JPA. */
    static class Chave implements Serializable {

        private UUID cargaId;
        private String tabela;

        Chave() {
        }

        Chave(UUID cargaId, String tabela) {
            this.cargaId = cargaId;
            this.tabela = tabela;
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
