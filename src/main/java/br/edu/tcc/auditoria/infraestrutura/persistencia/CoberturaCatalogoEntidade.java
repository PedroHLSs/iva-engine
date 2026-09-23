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

// Representa a cobertura que uma carga declarou para uma tabela. É o que separa "a carga não traz este registro" de "esta tabela não foi carregada nesta data"; sem a linha, a regra responde não avaliado em vez de apontar.
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

    // Fim null quer dizer vigência aberta, e não uma data distante escolhida à toa.
    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    // Construtor vazio exigido pelo JPA.
    protected CoberturaCatalogoEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
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

    // Representa a chave composta, carga e tabela, exigida pelo JPA.
    static class Chave implements Serializable {

        private UUID cargaId;
        private String tabela;

        // Construtor vazio exigido pelo JPA.
        Chave() {
        }

        // Construtor que recebe a carga e a tabela.
        Chave(UUID cargaId, String tabela) {
            this.cargaId = cargaId;
            this.tabela = tabela;
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
