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

// Representa uma linha da tabela de classificação tributária de uma carga; espelha a ClassificacaoTributaria do domínio. percentualReducao null quer dizer que a carga não declarou redução, o que é diferente de redução zero, e a coluna mantém as casas decimais importadas. tributacaoIntegral (V14, 30/09/2026) segue a mesma lógica: null é "não declarado", nunca "não é integral". anexosAdmitidos (V15, 30/09/2026) também: null é "não declarado", NENHUM é "não exige anexo", e os demais valores são identificadores separados por |.
// Emenda de 03/10/2026 (D015, V17): camposObrigatoriosDeclarados diz se a carga declarou os campos exigidos. A tabela filha sozinha não distingue a célula em branco de NENHUM — as duas ficavam sem linha.
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

    // V19 (03/10/2026, D017): ALIQUOTA ou BASE; null é "não declarado", e em carga anterior à V19.
    @Column(name = "reducao_incide_sobre")
    private String reducaoIncideSobre;

    @Column(name = "tributacao_integral")
    private Boolean tributacaoIntegral;

    @Column(name = "anexos_admitidos")
    private String anexosAdmitidos;

    // V17 (03/10/2026, D015): true quando a carga declarou os campos exigidos, inclusive NENHUM; false quando a célula veio em branco; null em carga anterior à V17.
    @Column(name = "campos_obrigatorios_declarados")
    private Boolean camposObrigatoriosDeclarados;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "fonte_normativa", nullable = false)
    private String fonteNormativa;

    // Conjunto, porque a ordem dos CSTs compatíveis não significa nada.
    @ElementCollection
    @CollectionTable(
            name = "classificacao_tributaria_cst",
            joinColumns = @JoinColumn(name = "classificacao_id"))
    @Column(name = "cst", nullable = false)
    private Set<String> cstsCompativeis = new LinkedHashSet<>();

    // Lista na ordem da carga, para a mensagem do apontamento não variar.
    @ElementCollection
    @CollectionTable(
            name = "classificacao_tributaria_campo_obrigatorio",
            joinColumns = @JoinColumn(name = "classificacao_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "campo", nullable = false)
    private List<String> camposObrigatoriosCondicionados = new ArrayList<>();

    // Construtor vazio exigido pelo JPA.
    protected ClassificacaoTributariaEntidade() {
    }

    // Construtor que recebe todos os campos da linha.
    ClassificacaoTributariaEntidade(
            UUID id,
            UUID cargaId,
            String codigo,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            BigDecimal percentualReducao,
            String reducaoIncideSobre,
            Boolean tributacaoIntegral,
            String anexosAdmitidos,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonteNormativa,
            Set<String> cstsCompativeis,
            boolean camposObrigatoriosDeclarados,
            List<String> camposObrigatoriosCondicionados) {

        this.id = id;
        this.cargaId = cargaId;
        this.codigo = codigo;
        this.dispositivoLegal = dispositivoLegal;
        this.indicadorDeBeneficio = indicadorDeBeneficio;
        this.percentualReducao = percentualReducao;
        this.reducaoIncideSobre = reducaoIncideSobre;
        this.tributacaoIntegral = tributacaoIntegral;
        this.anexosAdmitidos = anexosAdmitidos;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonteNormativa = fonteNormativa;
        this.cstsCompativeis = new LinkedHashSet<>(cstsCompativeis);
        this.camposObrigatoriosDeclarados = camposObrigatoriosDeclarados;
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

    String reducaoIncideSobre() {
        return reducaoIncideSobre;
    }

    Boolean tributacaoIntegral() {
        return tributacaoIntegral;
    }

    String anexosAdmitidos() {
        return anexosAdmitidos;
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

    Boolean camposObrigatoriosDeclarados() {
        return camposObrigatoriosDeclarados;
    }

    List<String> camposObrigatoriosCondicionados() {
        return camposObrigatoriosCondicionados;
    }
}
