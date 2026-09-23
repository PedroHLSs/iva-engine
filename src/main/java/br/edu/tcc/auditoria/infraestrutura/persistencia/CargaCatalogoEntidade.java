package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Representa a linha de uma carga de catálogo importada. A auditoria grava a versão da carga na execução, para um relatório antigo continuar dizendo contra qual catálogo foi feito.
@Entity
@Table(name = "carga_catalogo")
class CargaCatalogoEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "versao", nullable = false)
    private String versao;

    @Column(name = "importado_em", nullable = false)
    private Instant importadoEm;

    // Construtor vazio exigido pelo JPA.
    protected CargaCatalogoEntidade() {
    }

    // Construtor que recebe o identificador, a versão e o momento da importação.
    CargaCatalogoEntidade(UUID id, String versao, Instant importadoEm) {
        this.id = id;
        this.versao = versao;
        this.importadoEm = importadoEm;
    }

    UUID id() {
        return id;
    }

    String versao() {
        return versao;
    }

    Instant importadoEm() {
        return importadoEm;
    }
}
