package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma importação de catálogo normativo.
 *
 * <p>Cada carga é um conjunto próprio de registros. A auditoria usa a mais
 * recente e grava a versão dela na execução, para que um relatório antigo
 * continue dizendo contra qual catálogo foi produzido mesmo depois de novas
 * importações.</p>
 */
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

    protected CargaCatalogoEntidade() {
        // Exigido pelo JPA.
    }

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
