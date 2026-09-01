package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Linha da tabela de classificação tributária de uma carga.
 *
 * <p>Espelha {@code ClassificacaoTributaria} do domínio, sem nenhuma regra: o
 * domínio não tem anotação de JPA e não vai ter (D001), então o mapeamento entre
 * os dois é explícito e mora aqui.</p>
 *
 * <p>{@code percentualReducao} nulo significa que o catálogo não declarou
 * redução, e é diferente de redução de zero. A coluna é {@code numeric} sem
 * precisão declarada para que a escala do valor importado seja preservada.</p>
 */
@Entity
@Table(name = "classificacao_tributaria")
class ClassificacaoTributariaEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "carga_id", nullable = false)
    private UUID cargaId;

    @Column(name = "codigo", nullable = false)
    private String codigo;

    @Column(name = "dispositivo_legal", nullable = false)
    private String dispositivoLegal;

    @Column(name = "indicador_de_beneficio", nullable = false)
    private boolean indicadorDeBeneficio;

    @Column(name = "percentual_reducao")
    private BigDecimal percentualReducao;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    /** Conjunto: a ordem dos CSTs compatíveis não significa nada. */
    @ElementCollection
    @CollectionTable(
            name = "classificacao_tributaria_cst",
            joinColumns = @JoinColumn(name = "classificacao_id"))
    @Column(name = "cst", nullable = false)
    private Set<String> cstsCompativeis = new LinkedHashSet<>();

    /** Lista: a ordem da carga é preservada para que a mensagem do apontamento não varie. */
    @ElementCollection
    @CollectionTable(
            name = "classificacao_tributaria_campo_obrigatorio",
            joinColumns = @JoinColumn(name = "classificacao_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "campo", nullable = false)
    private List<String> camposObrigatoriosCondicionados = new ArrayList<>();

    protected ClassificacaoTributariaEntidade() {
        // Exigido pelo JPA.
    }

    ClassificacaoTributariaEntidade(
            UUID id,
            UUID cargaId,
            String codigo,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            BigDecimal percentualReducao,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonteNormativa,
            Set<String> cstsCompativeis,
            List<String> camposObrigatoriosCondicionados) {

        this.id = id;
        this.cargaId = cargaId;
        this.codigo = codigo;
        this.dispositivoLegal = dispositivoLegal;
        this.indicadorDeBeneficio = indicadorDeBeneficio;
        this.percentualReducao = percentualReducao;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
        this.cstsCompativeis = new LinkedHashSet<>(cstsCompativeis);
        this.camposObrigatoriosCondicionados = new ArrayList<>(camposObrigatoriosCondicionados);
    }

    String codigo() {
        return codigo;
    }

    String dispositivoLegal() {
        return dispositivoLegal;
    }

    boolean indicadorDeBeneficio() {
        return indicadorDeBeneficio;
    }

    BigDecimal percentualReducao() {
        return percentualReducao;
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

    Set<String> cstsCompativeis() {
        return cstsCompativeis;
    }

    List<String> camposObrigatoriosCondicionados() {
        return camposObrigatoriosCondicionados;
    }
}
