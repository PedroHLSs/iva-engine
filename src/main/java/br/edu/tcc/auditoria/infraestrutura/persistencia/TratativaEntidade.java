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

// Representa a decisão de uma pessoa sobre um apontamento. Não tem chave estrangeira para o apontamento, de propósito: fica presa ao hash do item, à regra e à versão, e por isso sobrevive ao reprocessamento e reabre quando a versão da regra muda.
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

    // Construtor vazio exigido pelo JPA.
    protected TratativaEntidade() {
    }

    // Construtor que recebe o identificador e a chave: hash do item, regra e versão.
    TratativaEntidade(UUID id, String hashItem, String regraId, String regraVersao) {
        this.id = id;
        this.hashItem = hashItem;
        this.regraId = regraId;
        this.regraVersao = regraVersao;
    }

    // Registra a decisão, substituindo a anterior da mesma chave.
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
