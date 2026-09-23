package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

// Representa um item de documento auditado, com os campos de IBS/CBS como vieram. Coluna null quer dizer campo não declarado, e nunca se grava zero no lugar; as colunas de valor mantêm as casas decimais, porque "0" e "0,00" não são o mesmo registro.
@Entity
@Table(name = "item_documento")
class ItemDocumentoEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "chave_acesso", nullable = false)
    private String chaveAcesso;

    @Column(name = "numero_item", nullable = false)
    private int numeroItem;

    @Column(name = "hash_item", nullable = false)
    private String hashItem;

    @Column(name = "ncm")
    private String ncm;

    @Column(name = "cfop")
    private String cfop;

    @Column(name = "valor_item", nullable = false)
    private BigDecimal valorItem;

    @Column(name = "cst_ibs")
    private String cstIbs;

    @Column(name = "cst_cbs")
    private String cstCbs;

    @Column(name = "codigo_classificacao_tributaria")
    private String codigoClassificacaoTributaria;

    @Column(name = "base_calculo_ibs")
    private BigDecimal baseCalculoIbs;

    @Column(name = "base_calculo_cbs")
    private BigDecimal baseCalculoCbs;

    @Column(name = "aliquota_ibs_uf")
    private BigDecimal aliquotaIbsUf;

    @Column(name = "aliquota_ibs_municipal")
    private BigDecimal aliquotaIbsMunicipal;

    @Column(name = "aliquota_cbs")
    private BigDecimal aliquotaCbs;

    @Column(name = "valor_ibs_uf")
    private BigDecimal valorIbsUf;

    @Column(name = "valor_ibs_municipal")
    private BigDecimal valorIbsMunicipal;

    @Column(name = "valor_cbs")
    private BigDecimal valorCbs;

    // Construtor vazio exigido pelo JPA.
    protected ItemDocumentoEntidade() {
    }

    // Construtor que recebe o identificador, a chave de acesso e o número do item.
    ItemDocumentoEntidade(UUID id, String chaveAcesso, int numeroItem) {
        this.id = id;
        this.chaveAcesso = chaveAcesso;
        this.numeroItem = numeroItem;
    }

    // Atualiza os campos com o estado atual do item e o hash dele.
    void atualizar(
            String hashItem,
            String ncm,
            String cfop,
            BigDecimal valorItem,
            String cstIbs,
            String cstCbs,
            String codigoClassificacaoTributaria,
            BigDecimal baseCalculoIbs,
            BigDecimal baseCalculoCbs,
            BigDecimal aliquotaIbsUf,
            BigDecimal aliquotaIbsMunicipal,
            BigDecimal aliquotaCbs,
            BigDecimal valorIbsUf,
            BigDecimal valorIbsMunicipal,
            BigDecimal valorCbs) {

        this.hashItem = hashItem;
        this.ncm = ncm;
        this.cfop = cfop;
        this.valorItem = valorItem;
        this.cstIbs = cstIbs;
        this.cstCbs = cstCbs;
        this.codigoClassificacaoTributaria = codigoClassificacaoTributaria;
        this.baseCalculoIbs = baseCalculoIbs;
        this.baseCalculoCbs = baseCalculoCbs;
        this.aliquotaIbsUf = aliquotaIbsUf;
        this.aliquotaIbsMunicipal = aliquotaIbsMunicipal;
        this.aliquotaCbs = aliquotaCbs;
        this.valorIbsUf = valorIbsUf;
        this.valorIbsMunicipal = valorIbsMunicipal;
        this.valorCbs = valorCbs;
    }

    // Acessores de leitura, acrescentados na Etapa 11 para a tela do produto; null aqui vira Optional vazio no mapeamento, nunca zero.
    String chaveAcesso() {
        return chaveAcesso;
    }

    int numeroItem() {
        return numeroItem;
    }

    String ncm() {
        return ncm;
    }

    String cfop() {
        return cfop;
    }

    BigDecimal valorItem() {
        return valorItem;
    }

    String cstIbs() {
        return cstIbs;
    }

    String cstCbs() {
        return cstCbs;
    }

    String codigoClassificacaoTributaria() {
        return codigoClassificacaoTributaria;
    }

    BigDecimal baseCalculoIbs() {
        return baseCalculoIbs;
    }

    BigDecimal baseCalculoCbs() {
        return baseCalculoCbs;
    }

    BigDecimal aliquotaIbsUf() {
        return aliquotaIbsUf;
    }

    BigDecimal aliquotaIbsMunicipal() {
        return aliquotaIbsMunicipal;
    }

    BigDecimal aliquotaCbs() {
        return aliquotaCbs;
    }

    BigDecimal valorIbsUf() {
        return valorIbsUf;
    }

    BigDecimal valorIbsMunicipal() {
        return valorIbsMunicipal;
    }

    BigDecimal valorCbs() {
        return valorCbs;
    }

    UUID id() {
        return id;
    }

    String hashItem() {
        return hashItem;
    }
}
