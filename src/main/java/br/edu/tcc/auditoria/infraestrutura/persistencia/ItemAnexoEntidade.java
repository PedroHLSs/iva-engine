package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

// Representa um vínculo entre NCM e anexo numa carga; espelha o ItemAnexo do domínio.
@Entity
@Table(name = "item_anexo")
class ItemAnexoEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Column(name = "ncm", nullable = false)
    private String ncm;

    @Column(name = "identificador_anexo", nullable = false)
    private String identificadorAnexo;

    @Column(name = "tipo_de_tratamento", nullable = false)
    private String tipoDeTratamento;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    // Construtor vazio exigido pelo JPA.
    protected ItemAnexoEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
    ItemAnexoEntidade(
            UUID id,
            UUID cargaId,
            String ncm,
            String identificadorAnexo,
            String tipoDeTratamento,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonteNormativa) {
        this.id = id;
        this.cargaId = cargaId;
        this.ncm = ncm;
        this.identificadorAnexo = identificadorAnexo;
        this.tipoDeTratamento = tipoDeTratamento;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
    }

    String ncm() {
        return ncm;
    }

    String identificadorAnexo() {
        return identificadorAnexo;
    }

    String tipoDeTratamento() {
        return tipoDeTratamento;
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
