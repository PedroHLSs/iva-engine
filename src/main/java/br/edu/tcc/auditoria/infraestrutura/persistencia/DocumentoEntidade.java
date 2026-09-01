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

/**
 * Documento fiscal auditado.
 *
 * <p>Chaveado pela chave de acesso, e não por um identificador gerado: o mesmo
 * documento reprocessado é o mesmo documento, e reprocessar um lote não pode
 * criar linha nova.</p>
 *
 * <p><strong>Nenhum dado pessoal em texto claro.</strong> Não há coluna de CNPJ,
 * CPF, razão social ou endereço; emitente e destinatário entram apenas como
 * resumo criptográfico calculado com o sal de instalação. O banco ainda recusa,
 * por restrição de formato, qualquer coisa que não seja o resumo.</p>
 */
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

    /** Nulo quando o documento não declarou destinatário com UF — destino no exterior, por exemplo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "uf_destinatario")
    private Uf ufDestinatario;

    @Column(name = "crt_emitente")
    private String crtEmitente;

    @Column(name = "indicador_destinatario")
    private String indicadorDestinatario;

    @Column(name = "emitente_pseudonimizado", nullable = false)
    private String emitentePseudonimizado;

    /** Nulo quando não há destinatário identificado — consumidor não identificado em NFC-e. */
    @Column(name = "destinatario_pseudonimizado")
    private String destinatarioPseudonimizado;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected DocumentoEntidade() {
        // Exigido pelo JPA.
    }

    DocumentoEntidade(String chaveAcesso) {
        this.chaveAcesso = chaveAcesso;
    }

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

    // Leitura acrescentada na Etapa 6: o papel de trabalho identifica o documento
    // por modelo, série, número, data e UF, e não pela chave de acesso, cujos
    // dígitos carregam o CNPJ do emitente. Não há acessor para as colunas de
    // pseudônimo de participante, de propósito: a exportação não precisa delas.

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
