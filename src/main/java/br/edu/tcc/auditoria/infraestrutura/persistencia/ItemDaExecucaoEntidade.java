package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Um item que uma execução leu, com ou sem apontamento. */
@Entity
@Table(name = "item_da_execucao")
class ItemDaExecucaoEntidade {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "execucao_id", nullable = false)
    private UUID execucaoId;

    @Column(name = "chave_acesso", nullable = false)
    private String chaveAcesso;

    @Column(name = "numero_item", nullable = false)
    private int numeroItem;

    /** O resumo que ESTA execução leu, e não o que estiver em item_documento hoje. */
    @Column(name = "hash_item", nullable = false)
    private String hashItem;

    /*
     * Acrescentadas na etapa de conferência, pela V8.
     *
     * As duas colunas são o par "valor ou motivo" da D009 levado ao banco, e a
     * restrição da V8 garante que exatamente uma delas esteja preenchida. Elas
     * ficam AQUI, e não em tabela à parte, porque esta linha já identifica a
     * leitura daquele item: a descrição não tem chave própria e por isso não tem
     * como divergir do item que descreve.
     */
    @Column(name = "descricao_produto")
    private String descricaoProduto;

    @Column(name = "motivo_sem_descricao")
    private String motivoSemDescricao;

    protected ItemDaExecucaoEntidade() {
        // Exigido pelo JPA.
    }

    ItemDaExecucaoEntidade(
            UUID id,
            UUID execucaoId,
            String chaveAcesso,
            int numeroItem,
            String hashItem,
            DescricaoDoProduto descricao) {

        this.id = id;
        this.execucaoId = execucaoId;
        this.chaveAcesso = chaveAcesso;
        this.numeroItem = numeroItem;
        this.hashItem = hashItem;
        this.descricaoProduto = descricao.texto().orElse(null);
        this.motivoSemDescricao = descricao.motivoDaAusencia().orElse(null);
    }

    String chaveAcesso() {
        return chaveAcesso;
    }

    int numeroItem() {
        return numeroItem;
    }

    String hashItem() {
        return hashItem;
    }

    /**
     * As duas colunas de volta como um estado só.
     *
     * <p>A terceira possibilidade — as duas nulas — só existe em linha gravada
     * antes da V8 num banco onde o preenchimento retroativo dela não rodou. Ela é
     * traduzida para a ausência que diz isso, e não para "o documento não
     * declarou": a falta seria do sistema, e pô-la no documento seria mentir sobre
     * o que o emitente escreveu.</p>
     */
    DescricaoDoProduto descricao() {
        if (descricaoProduto != null) {
            return new DescricaoDoProduto.Declarada(descricaoProduto);
        }
        if (motivoSemDescricao != null) {
            return new DescricaoDoProduto.NaoDeclarada(motivoSemDescricao);
        }
        return DescricaoDoProduto.anteriorAoRegistro();
    }
}
