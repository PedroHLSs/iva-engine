package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Decisão humana gravada sobre um apontamento.
 *
 * <p><strong>Não há chave estrangeira para o apontamento, de propósito.</strong>
 * A tratativa é presa a {@code (hashItem, regraId, regraVersao)} — conteúdo do
 * item e critério aplicado —, e não à linha do apontamento. É o que a faz
 * sobreviver ao reprocessamento do lote: o apontamento pode ser regravado, a
 * tratativa continua sendo reencontrada.</p>
 *
 * <p>Pelo mesmo motivo, mudar a versão da regra faz o apontamento reabrir: a
 * chave deixa de bater e a decisão antiga permanece gravada, amarrada à versão
 * em que foi dada.</p>
 */
@Entity
@Table(name = "tratativa")
class TratativaEntidade {

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
    @Column(name = "decisao", nullable = false)
    private DecisaoDeTratativa decisao;

    @Column(name = "justificativa", nullable = false)
    private String justificativa;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected TratativaEntidade() {
        // Exigido pelo JPA.
    }

    TratativaEntidade(UUID id, String hashItem, String regraId, String regraVersao) {
        this.id = id;
        this.hashItem = hashItem;
        this.regraId = regraId;
        this.regraVersao = regraVersao;
    }

    /** Registra a decisão, substituindo a anterior sobre a mesma chave. */
    void decidir(DecisaoDeTratativa decisao, String justificativa, Instant registradoEm) {
        this.decisao = decisao;
        this.justificativa = justificativa;
        this.registradoEm = registradoEm;
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

    DecisaoDeTratativa decisao() {
        return decisao;
    }

    String justificativa() {
        return justificativa;
    }

    Instant registradoEm() {
        return registradoEm;
    }
}
