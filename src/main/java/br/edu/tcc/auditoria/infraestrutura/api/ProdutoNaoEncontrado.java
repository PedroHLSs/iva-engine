package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.UUID;

/**
 * Produto pedido que não está naquela análise. Sai como 404.
 *
 * <p>O endereço do produto é o resumo do item, e ele muda quando o conteúdo do
 * item muda. Então esta recusa cobre dois casos honestos: o endereço nunca
 * existiu, e o item foi reprocessado com conteúdo diferente depois desta análise
 * — caso em que o endereço antigo deixou de apontar para alguma coisa.</p>
 *
 * <p>A mensagem não repete o endereço. Ele é um resumo criptográfico, não carrega
 * identificador em texto claro, mas devolver entrada de fora dentro de mensagem
 * de erro é hábito que não compensa começar.</p>
 */
public class ProdutoNaoEncontrado extends RuntimeException {

    public ProdutoNaoEncontrado(UUID analiseId) {
        super(("A análise %s não tem produto com esse endereço. Ou ele não é dela, ou o item foi "
                + "reprocessado com outro conteúdo depois dela — o endereço é o resumo do item, e "
                + "muda quando o item muda.").formatted(analiseId));
    }
}
