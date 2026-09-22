package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Produtos que compartilham enquadramento declarado e situação.
 *
 * <h2>{@code valorDosProdutos} não é valor em risco</h2>
 *
 * <p>É a soma do valor dos itens que caíram neste grupo — o tamanho da operação
 * envolvida, não o tamanho do erro. O nome é longo de propósito: abreviado para
 * "valor", numa coluna ao lado de uma situação de divergência, ele seria lido
 * como o prejuízo, que é outra coisa e mora no apontamento
 * ({@code ValorEmRisco}).</p>
 *
 * <p>A soma é sempre calculável: {@code valorItem} é obrigatório no item. Por
 * isso este tipo não tem o par "valor ou motivo" que os apontamentos têm — aqui
 * não existe o caso de não haver valor.</p>
 *
 * <h2>Notas e produtos são contagens diferentes</h2>
 *
 * <p>Quatrocentas ocorrências podem estar em quatrocentas notas ou em doze. A
 * primeira situação é erro de cadastro espalhado; a segunda é um punhado de notas
 * com muitos itens iguais. Uma contagem só não separa as duas.</p>
 */
public record GrupoDeProdutos(
        ChaveDoGrupo chave,
        ResumoDaConferencia resumo,
        BigDecimal valorDosProdutos,
        int quantidadeDeNotas,
        List<ProdutoConferido> produtos) {

    /** O rótulo que a soma tem de carregar em qualquer tela. */
    public static final String ROTULO_DO_VALOR = "valor dos produtos envolvidos";

    public GrupoDeProdutos {
        if (chave == null) {
            throw new ConferenciaInvalida("O grupo precisa da chave que o define.");
        }
        if (resumo == null) {
            throw new ConferenciaInvalida("O grupo precisa do resumo dos produtos dele.");
        }
        if (valorDosProdutos == null) {
            throw new ConferenciaInvalida(
                    "O valor dos produtos envolvidos é soma de campo obrigatório do item e nunca é "
                            + "nulo.");
        }
        if (produtos == null || produtos.isEmpty()) {
            throw new ConferenciaInvalida(
                    "Grupo sem produto não é grupo: ele existe porque produtos caíram nele.");
        }
        if (produtos.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A lista de produtos do grupo não pode conter elemento nulo.");
        }
        if (resumo.quantidadeDeProdutos() != produtos.size()) {
            throw new ConferenciaInvalida(
                    ("O resumo do grupo conta %d produto(s) e a lista traz %d.")
                            .formatted(resumo.quantidadeDeProdutos(), produtos.size()));
        }
        if (quantidadeDeNotas < 1 || quantidadeDeNotas > produtos.size()) {
            throw new ConferenciaInvalida(
                    ("O grupo tem %d produto(s) e diz vir de %d nota(s). Um produto pertence a uma nota, "
                            + "então não pode haver mais notas que produtos.")
                            .formatted(produtos.size(), quantidadeDeNotas));
        }
        produtos = List.copyOf(produtos);
    }

    /** Monta o grupo a partir dos produtos que caíram nele. */
    public static GrupoDeProdutos de(ChaveDoGrupo chave, List<ProdutoConferido> produtos) {
        if (produtos == null || produtos.isEmpty()) {
            throw new ConferenciaInvalida("Não há produtos com que montar o grupo.");
        }
        BigDecimal valor = BigDecimal.ZERO;
        Set<ChaveAcesso> notas = new LinkedHashSet<>();
        for (ProdutoConferido produto : produtos) {
            valor = valor.add(produto.dados().item().valorItem());
            notas.add(produto.dados().chaveAcesso());
        }
        return new GrupoDeProdutos(
                chave,
                ResumoDaConferencia.de(produtos.stream().map(ProdutoConferido::situacao).toList()),
                valor,
                notas.size(),
                produtos);
    }

    public int quantidadeDeProdutos() {
        return produtos.size();
    }

    /** Quanto da chave o documento deu — vai escrito, ver {@link NivelDoAgrupamento}. */
    public NivelDoAgrupamento nivel() {
        return chave.nivel();
    }
}
