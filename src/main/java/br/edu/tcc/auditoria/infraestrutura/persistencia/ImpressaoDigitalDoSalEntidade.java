package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A impressão digital do sal de instalação, em linha única.
 *
 * <p>Guarda o resumo do sal, nunca o sal. Ver a migration {@code V6} para o
 * motivo de a tabela existir.</p>
 */
@Entity
@Table(name = "impressao_digital_do_sal")
class ImpressaoDigitalDoSalEntidade {

    /** A tabela tem uma linha só, e a restrição do banco garante isso. */
    static final short LINHA_UNICA = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "valor", nullable = false)
    private String valor;

    @Column(name = "registrada_em", nullable = false)
    private Instant registradaEm;

    @Column(name = "origem", nullable = false)
    private String origem;

    @Column(name = "adotada_de_acervo_existente", nullable = false)
    private boolean adotadaDeAcervoExistente;

    protected ImpressaoDigitalDoSalEntidade() {
        // Exigido pelo JPA.
    }

    ImpressaoDigitalDoSalEntidade(
            String valor, Instant registradaEm, String origem, boolean adotadaDeAcervoExistente) {
        this.id = LINHA_UNICA;
        this.valor = valor;
        this.registradaEm = registradaEm;
        this.origem = origem;
        this.adotadaDeAcervoExistente = adotadaDeAcervoExistente;
    }

    String valor() {
        return valor;
    }

    String origem() {
        return origem;
    }

    boolean adotadaDeAcervoExistente() {
        return adotadaDeAcervoExistente;
    }

    Instant registradaEm() {
        return registradaEm;
    }
}
