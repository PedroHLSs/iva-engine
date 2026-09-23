package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.UUID;

public class ProdutoNaoEncontrado extends RuntimeException {

    // Construtor que recebe o identificador da análise e chama RuntimeException com a mensagem de erro.
    public ProdutoNaoEncontrado(UUID analiseId) {
        super(("A análise %s não tem produto com esse endereço. Ou ele não é dela, ou o item foi "
                + "reprocessado com outro conteúdo depois dela — o endereço é o resumo do item, e "
                + "muda quando o item muda.").formatted(analiseId));
    }
}
