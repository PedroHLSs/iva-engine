package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// Representa a linha única com a impressão digital do sal da instalação; guarda o resumo, nunca o sal.
@Entity
@Table(name = "impressao_digital_do_sal")
class ImpressaoDigitalDoSalEntidade {

    // Identificador da única linha; a restrição do banco garante que só há uma.
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

    // Construtor vazio exigido pelo JPA.
    protected ImpressaoDigitalDoSalEntidade() {
    }

    // Construtor que recebe a impressão digital, quando foi registrada, a origem e se foi adotada sobre acervo existente.
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
