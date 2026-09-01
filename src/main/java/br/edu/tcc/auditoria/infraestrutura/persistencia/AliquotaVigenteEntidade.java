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

/**
 * Percentual importado para um tributo numa abrangência e numa vigência.
 *
 * <p>O percentual é {@code numeric} sem precisão declarada: a escala com que o
 * valor foi importado é preservada. Nenhum percentual está escrito em código,
 * aqui ou em qualquer outro lugar do sistema.</p>
 *
 * <p>{@code tributo} é gravado pelo nome da constante, e não pelo ordinal:
 * reordenar o enum não pode reescrever o significado de linha já gravada.</p>
 */
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

    protected AliquotaVigenteEntidade() {
        // Exigido pelo JPA.
    }

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
