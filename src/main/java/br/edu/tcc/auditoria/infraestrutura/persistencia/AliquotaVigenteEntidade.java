package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Representa um percentual importado para um tributo, numa abrangência e num período. O percentual mantém as casas decimais da importação, e o tributo é gravado pelo nome, e não pela posição no enum, para reordenar o enum não mudar linha gravada.
@Entity
@Table(name = "aliquota_vigente")
class AliquotaVigenteEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tributo", nullable = false)
    private Tributo tributo;

    @Column(name = "percentual", nullable = false)
    private BigDecimal percentual;

    @Column(name = "abrangencia", nullable = false)
    private String abrangencia;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    // Construtor vazio exigido pelo JPA.
    protected AliquotaVigenteEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
    AliquotaVigenteEntidade(
            UUID id,
            UUID cargaId,
            Tributo tributo,
            BigDecimal percentual,
            String abrangencia,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonteNormativa) {
        this.id = id;
        this.cargaId = cargaId;
        this.tributo = tributo;
        this.percentual = percentual;
        this.abrangencia = abrangencia;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
    }

    Tributo tributo() {
        return tributo;
    }

    BigDecimal percentual() {
        return percentual;
    }

    String abrangencia() {
        return abrangencia;
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
