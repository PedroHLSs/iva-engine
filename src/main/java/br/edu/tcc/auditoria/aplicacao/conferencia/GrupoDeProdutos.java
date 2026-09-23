package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Representa os produtos que compartilham enquadramento declarado e situação; o valor dos produtos não é valor em risco.
public record GrupoDeProdutos(
        ChaveDoGrupo chave,
        ResumoDaConferencia resumo,
        BigDecimal valorDosProdutos,
        int quantidadeDeNotas,
        List<ProdutoConferido> produtos) {

    // Rótulo que a soma do valor tem de carregar em qualquer tela.
    public static final String ROTULO_DO_VALOR = "valor dos produtos envolvidos";

    // Valida o grupo: exige produtos e confere o resumo e a quantidade de notas.
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

    // Método estático que monta o grupo somando o valor dos itens e contando as notas distintas.
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

    // Retorna o nível do agrupamento, isto é, quanto da chave o documento declarou.
    public NivelDoAgrupamento nivel() {
        return chave.nivel();
    }
}
