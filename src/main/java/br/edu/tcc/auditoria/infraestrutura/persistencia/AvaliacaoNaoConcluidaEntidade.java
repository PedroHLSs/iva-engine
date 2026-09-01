package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Uma avaliação que não concluiu, numa execução.
 *
 * <p>Escopada por execução e sem deduplicação, ao contrário do apontamento: não
 * concluir é fato da rodada, não do documento. Rodar de novo com um catálogo
 * mais completo produz outro resultado, e os dois precisam continuar existindo
 * lado a lado — ver {@code V5__nao_avaliado_e_achado_por_execucao.sql}.</p>
 */
@Entity
@Table(name = "avaliacao_nao_concluida")
class AvaliacaoNaoConcluidaEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "execucao_id", nullable = false)
    private UUID execucaoId;

    @Column(name = "chave_acesso", nullable = false)
    private String chaveAcesso;

    @Column(name = "numero_item", nullable = false)
    private int numeroItem;

    @Column(name = "regra_id", nullable = false)
    private String regraId;

    @Column(name = "regra_versao", nullable = false)
    private String regraVersao;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    protected AvaliacaoNaoConcluidaEntidade() {
        // Exigido pelo JPA.
    }

    AvaliacaoNaoConcluidaEntidade(
            UUID id,
            UUID execucaoId,
            String chaveAcesso,
            int numeroItem,
            String regraId,
            String regraVersao,
            String motivo) {
        this.id = id;
        this.execucaoId = execucaoId;
        this.chaveAcesso = chaveAcesso;
        this.numeroItem = numeroItem;
        this.regraId = regraId;
        this.regraVersao = regraVersao;
        this.motivo = motivo;
    }

    String chaveAcesso() {
        return chaveAcesso;
    }

    int numeroItem() {
        return numeroItem;
    }

    String regraId() {
        return regraId;
    }

    String regraVersao() {
        return regraVersao;
    }

    String motivo() {
        return motivo;
    }
}
