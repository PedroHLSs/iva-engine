package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Uf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

// Representa um documento auditado, com a chave de acesso como identificador, para reprocessar o lote não criar linha nova. Não tem CNPJ, CPF, razão social nem endereço: os participantes entram só como pseudônimo, e o banco recusa outra coisa.
@Entity
@Table(name = "documento")
class DocumentoEntidade {

    @Id
    @Column(name = "chave_acesso", nullable = false)
    private String chaveAcesso;

    @Column(name = "modelo", nullable = false)
    private String modelo;

    @Column(name = "serie", nullable = false)
    private String serie;

    @Column(name = "numero", nullable = false)
    private String numero;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Enumerated(EnumType.STRING)
    @Column(name = "uf_emitente", nullable = false)
    private Uf ufEmitente;

    // Null quando a nota não declarou destinatário com UF, como destino no exterior.
    @Enumerated(EnumType.STRING)
    @Column(name = "uf_destinatario")
    private Uf ufDestinatario;

    @Column(name = "crt_emitente")
    private String crtEmitente;

    @Column(name = "indicador_destinatario")
    private String indicadorDestinatario;

    @Column(name = "emitente_pseudonimizado", nullable = false)
    private String emitentePseudonimizado;

    // Null quando não há destinatário identificado, como consumidor não identificado em NFC-e.
    @Column(name = "destinatario_pseudonimizado")
    private String destinatarioPseudonimizado;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    // Construtor vazio exigido pelo JPA.
    protected DocumentoEntidade() {
    }

    // Construtor que recebe a chave de acesso.
    DocumentoEntidade(String chaveAcesso) {
        this.chaveAcesso = chaveAcesso;
    }

    // Atualiza os campos com o estado atual do documento.
    void atualizar(
            String modelo,
            String serie,
            String numero,
            LocalDate dataEmissao,
            Uf ufEmitente,
            Uf ufDestinatario,
            String crtEmitente,
            String indicadorDestinatario,
            String emitentePseudonimizado,
            String destinatarioPseudonimizado,
            Instant registradoEm) {

        this.modelo = modelo;
        this.serie = serie;
        this.numero = numero;
        this.dataEmissao = dataEmissao;
        this.ufEmitente = ufEmitente;
        this.ufDestinatario = ufDestinatario;
        this.crtEmitente = crtEmitente;
        this.indicadorDestinatario = indicadorDestinatario;
        this.emitentePseudonimizado = emitentePseudonimizado;
        this.destinatarioPseudonimizado = destinatarioPseudonimizado;
        this.registradoEm = registradoEm;
    }

    String chaveAcesso() {
        return chaveAcesso;
    }

    LocalDate dataEmissao() {
        return dataEmissao;
    }

    // Acessores acrescentados na Etapa 6, para a planilha identificar a nota sem a chave de acesso; de propósito, não há acessor para os pseudônimos dos participantes.

    String modelo() {
        return modelo;
    }

    String serie() {
        return serie;
    }

    String numero() {
        return numero;
    }

    Uf ufEmitente() {
        return ufEmitente;
    }
}
