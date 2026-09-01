package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Severidade;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Apontamento gravado.
 *
 * <h2>A identidade não é a linha</h2>
 *
 * <p>A chave natural do apontamento é {@code (hashItem, regraId, regraVersao)} —
 * a mesma chave da tratativa. Reprocessar o mesmo lote reencontra a linha em vez
 * de criar outra: o conteúdo é atualizado, {@code ultimaExecucaoId} e
 * {@code vistoEm} avançam, e {@code primeiraExecucaoId} e {@code detectadoEm}
 * ficam como estavam. É isso que faz a tratativa dada por uma pessoa continuar
 * valendo depois do reprocessamento.</p>
 *
 * <p>O identificador gerado existe só para que a linha possa ser citada — é o
 * que a listagem mostra e o que o comando de tratar recebe.</p>
 *
 * <h2>Valor em risco</h2>
 *
 * <p>Ou há montante, ou há o motivo de não haver. Nunca nenhum dos dois e nunca
 * os dois: uma restrição do banco garante isso, porque valor ausente sem
 * explicação seria exatamente o silêncio que este projeto evita.</p>
 */
@Entity
@Table(name = "achado")
class AchadoEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "hash_item", nullable = false)
    private String hashItem;

    @Column(name = "regra_id", nullable = false)
    private String regraId;

    @Column(name = "regra_versao", nullable = false)
    private String regraVersao;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidade", nullable = false)
    private Severidade severidade;

    @Column(name = "chave_acesso", nullable = false)
    private String chaveAcesso;

    @Column(name = "numero_item", nullable = false)
    private int numeroItem;

    @Column(name = "fundamento_normativo", nullable = false)
    private String fundamentoNormativo;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    /** Nulo é vigência aberta. */
    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "valor_em_risco")
    private BigDecimal valorEmRisco;

    @Column(name = "motivo_valor_ausente")
    private String motivoValorAusente;

    @Column(name = "primeira_execucao_id", nullable = false)
    private UUID primeiraExecucaoId;

    @Column(name = "ultima_execucao_id", nullable = false)
    private UUID ultimaExecucaoId;

    @Column(name = "detectado_em", nullable = false)
    private Instant detectadoEm;

    @Column(name = "visto_em", nullable = false)
    private Instant vistoEm;

    @ElementCollection
    @CollectionTable(name = "achado_evidencia", joinColumns = @JoinColumn(name = "achado_id"))
    @OrderColumn(name = "ordem")
    private List<EvidenciaEmbutida> evidencias = new ArrayList<>();

    protected AchadoEntidade() {
        // Exigido pelo JPA.
    }

    AchadoEntidade(
            UUID id,
            String hashItem,
            String regraId,
            String regraVersao,
            UUID execucaoId,
            Instant detectadoEm) {
        this.id = id;
        this.hashItem = hashItem;
        this.regraId = regraId;
        this.regraVersao = regraVersao;
        this.primeiraExecucaoId = execucaoId;
        this.ultimaExecucaoId = execucaoId;
        this.detectadoEm = detectadoEm;
        this.vistoEm = detectadoEm;
    }

    /**
     * Regrava o conteúdo do apontamento e marca que ele foi visto de novo.
     *
     * <p>O conteúdo pode mudar entre execuções sem que o apontamento deixe de ser
     * o mesmo: a fonte normativa citada vem do catálogo, e uma carga nova pode
     * trazer outra redação para a mesma regra e o mesmo item. Vale o último
     * conteúdo; a identidade e a primeira detecção não se mexem.</p>
     */
    void registrarOcorrencia(
            Severidade severidade,
            String chaveAcesso,
            int numeroItem,
            String fundamentoNormativo,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            BigDecimal valorEmRisco,
            String motivoValorAusente,
            List<EvidenciaEmbutida> evidencias,
            UUID execucaoId,
            Instant vistoEm) {

        this.severidade = severidade;
        this.chaveAcesso = chaveAcesso;
        this.numeroItem = numeroItem;
        this.fundamentoNormativo = fundamentoNormativo;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.valorEmRisco = valorEmRisco;
        this.motivoValorAusente = motivoValorAusente;
        this.ultimaExecucaoId = execucaoId;
        this.vistoEm = vistoEm;

        this.evidencias.clear();
        this.evidencias.addAll(evidencias);
    }

    UUID id() {
        return id;
    }

    String hashItem() {
        return hashItem;
    }

    String regraId() {
        return regraId;
    }

    String regraVersao() {
        return regraVersao;
    }

    Severidade severidade() {
        return severidade;
    }

    String chaveAcesso() {
        return chaveAcesso;
    }

    int numeroItem() {
        return numeroItem;
    }

    String fundamentoNormativo() {
        return fundamentoNormativo;
    }

    LocalDate vigenciaInicio() {
        return vigenciaInicio;
    }

    LocalDate vigenciaFim() {
        return vigenciaFim;
    }

    BigDecimal valorEmRisco() {
        return valorEmRisco;
    }

    String motivoValorAusente() {
        return motivoValorAusente;
    }

    Instant detectadoEm() {
        return detectadoEm;
    }

    Instant vistoEm() {
        return vistoEm;
    }

    List<EvidenciaEmbutida> evidencias() {
        return evidencias;
    }
}
