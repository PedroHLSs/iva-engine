package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

// Representa uma linha da tabela de NCM de uma carga; espelha o RegistroNcm do domínio.
@Entity
@Table(name = "registro_ncm")
class RegistroNcmEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Column(name = "ncm", nullable = false)
    private String ncm;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    // Construtor vazio exigido pelo JPA.
    protected RegistroNcmEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
    RegistroNcmEntidade(
            UUID id,
            UUID cargaId,
            String ncm,
            String descricao,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonteNormativa) {
        this.id = id;
        this.cargaId = cargaId;
        this.ncm = ncm;
        this.descricao = descricao;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
    }

    String ncm() {
        return ncm;
    }

    String descricao() {
        return descricao;
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
}
