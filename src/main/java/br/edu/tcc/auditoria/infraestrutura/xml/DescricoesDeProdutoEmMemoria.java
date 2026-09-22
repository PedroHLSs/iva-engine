package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Guarda as descrições lidas em <strong>uma</strong> análise.
 *
 * <h2>Este objeto é de uma análise, e de mais nenhuma</h2>
 *
 * <p>Ele é criado pela fábrica de leitura a cada análise e descartado com ela.
 * Não é bean, não é compartilhado e não sobrevive à requisição. O registro de
 * falhas da Etapa 4 tinha a forma contrária — um objeto vivo enquanto o processo
 * vive —, o que bastava para a linha de comando e deixou de bastar quando a API
 * passou a atender análises em sequência. Aqui o problema não chega a existir.</p>
 *
 * <h2>Endereçado pelo resumo do item</h2>
 *
 * <p>A chave é {@link HashDoItem}, a mesma identidade que o acervo grava e que a
 * interface usa como endereço do produto. Quem grava e quem lê calculam o
 * endereço com a mesma função, sobre as mesmas entradas: descrição ligada ao
 * produto errado é impossível por construção.</p>
 *
 * <p>O mapa é sincronizado porque o fluxo do {@link LeitorLote} pode ser
 * consumido em paralelo, como o de falhas. Item repetido no lote — o mesmo
 * arquivo duas vezes dentro do pacote — sobrescreve com o mesmo conteúdo, que é
 * o resultado certo.</p>
 */
public final class DescricoesDeProdutoEmMemoria implements RegistroDeDescricoesDeProduto {

    private final Map<HashDoItem, Optional<String>> descricoes =
            Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public void registrar(DescricaoDeProdutoLida lida) {
        if (lida == null) {
            throw new IllegalArgumentException("Não se registra descrição nula.");
        }
        descricoes.put(lida.hashDoItem(), lida.descricao());
    }

    /**
     * A descrição registrada para aquele item.
     *
     * <p>Três respostas, e as três diferentes: {@code Optional.empty()} no
     * retorno externo significa que este item não passou por esta leitura;
     * presente com {@code Optional.empty()} dentro significa que passou e o
     * documento não declarou nada; presente com texto é o texto.</p>
     */
    public Optional<Optional<String>> de(HashDoItem hashDoItem) {
        if (hashDoItem == null) {
            return Optional.empty();
        }
        synchronized (descricoes) {
            return Optional.ofNullable(descricoes.get(hashDoItem));
        }
    }

    public int quantidade() {
        return descricoes.size();
    }

    public boolean vazio() {
        return descricoes.isEmpty();
    }
}
