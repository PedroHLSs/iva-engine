package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Representa um item que uma execução leu, com ou sem apontamento.
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

    // O hash que ESTA execução leu, e não o que estiver em item_documento hoje.
    @Column(name = "hash_item", nullable = false)
    private String hashItem;

    // Descrição do produto ou motivo de não haver: a V8 garante que só uma das duas colunas vem preenchida. Acrescentadas na Etapa 11, nesta linha, para a descrição não ter como se separar do item.
    @Column(name = "descricao_produto")
    private String descricaoProduto;

    @Column(name = "motivo_sem_descricao")
    private String motivoSemDescricao;

    // Construtor vazio exigido pelo JPA.
    protected ItemDaExecucaoEntidade() {
    }

    // Construtor que recebe todos os campos da linha, com a descrição ou o motivo de não haver.
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

    // Devolve a descrição num estado só. As duas colunas vazias só acontecem em linha gravada antes da V8, e isso é dito, em vez de culpar o documento.
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
