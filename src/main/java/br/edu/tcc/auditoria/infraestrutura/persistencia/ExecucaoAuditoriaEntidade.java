package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Severidade;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Representa o recibo gravado de uma rodada de auditoria. As contagens por gravidade e por regra ficam em tabelas próprias, e não em colunas fixas, para regra nova não exigir migration; regra que não apontou nada aparece com zero.
@Entity
@Table(name = "execucao_auditoria")
class ExecucaoAuditoriaEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(name = "hash_entrada", nullable = false)
    private String hashEntrada;

    @Column(name = "versao_catalogo", nullable = false)
    private String versaoCatalogo;

    @Column(name = "versao_conjunto_regras", nullable = false)
    private String versaoConjuntoRegras;

    @Column(name = "quantidade_documentos", nullable = false)
    private int quantidadeDocumentos;

    @Column(name = "quantidade_itens", nullable = false)
    private int quantidadeItens;

    @ElementCollection
    @CollectionTable(
            name = "execucao_achado_por_severidade",
            joinColumns = @JoinColumn(name = "execucao_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "severidade")
    @Column(name = "quantidade", nullable = false)
    private Map<Severidade, Integer> achadosPorSeveridade = new EnumMap<>(Severidade.class);

    @ElementCollection
    @CollectionTable(
            name = "execucao_achado_por_regra",
            joinColumns = @JoinColumn(name = "execucao_id"))
    @MapKeyColumn(name = "regra_id")
    @Column(name = "quantidade", nullable = false)
    private Map<String, Integer> achadosPorRegra = new LinkedHashMap<>();

    // Construtor vazio exigido pelo JPA.
    protected ExecucaoAuditoriaEntidade() {
    }

    // Construtor que recebe todos os campos do recibo, com as duas contagens.
    ExecucaoAuditoriaEntidade(
            UUID id,
            Instant dataHora,
            String hashEntrada,
            String versaoCatalogo,
            String versaoConjuntoRegras,
            int quantidadeDocumentos,
            int quantidadeItens,
            Map<Severidade, Integer> achadosPorSeveridade,
            Map<String, Integer> achadosPorRegra) {

        this.id = id;
        this.dataHora = dataHora;
        this.hashEntrada = hashEntrada;
        this.versaoCatalogo = versaoCatalogo;
        this.versaoConjuntoRegras = versaoConjuntoRegras;
        this.quantidadeDocumentos = quantidadeDocumentos;
        this.quantidadeItens = quantidadeItens;
        // Usa EnumMap.putAll, e não o construtor de cópia, porque ele recusa mapa vazio que não seja EnumMap.
        this.achadosPorSeveridade = new EnumMap<>(Severidade.class);
        this.achadosPorSeveridade.putAll(achadosPorSeveridade);
        this.achadosPorRegra = new LinkedHashMap<>(achadosPorRegra);
    }

    UUID id() {
        return id;
    }

    Instant dataHora() {
        return dataHora;
    }

    String hashEntrada() {
        return hashEntrada;
    }

    String versaoCatalogo() {
        return versaoCatalogo;
    }

    String versaoConjuntoRegras() {
        return versaoConjuntoRegras;
    }

    int quantidadeDocumentos() {
        return quantidadeDocumentos;
    }

    int quantidadeItens() {
        return quantidadeItens;
    }

    Map<Severidade, Integer> achadosPorSeveridade() {
        return achadosPorSeveridade;
    }

    Map<String, Integer> achadosPorRegra() {
        return achadosPorRegra;
    }
}
