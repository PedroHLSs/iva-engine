package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Classe que guarda as descrições lidas em uma análise só: nasce e morre com ela e não é bean, para uma análise não ver as descrições de outra. A chave é o HashDoItem, e o mapa é sincronizado porque a leitura pode ser em paralelo.
public final class DescricoesDeProdutoEmMemoria implements RegistroDeDescricoesDeProduto {

    private final Map<HashDoItem, Optional<String>> descricoes =
            Collections.synchronizedMap(new LinkedHashMap<>());

    // Registra a descrição de um item; item repetido fica com o mesmo conteúdo.
    @Override
    public void registrar(DescricaoDeProdutoLida lida) {
        if (lida == null) {
            throw new IllegalArgumentException("Não se registra descrição nula.");
        }
        descricoes.put(lida.hashDoItem(), lida.descricao());
    }

    // Devolve a descrição do item em três casos: vazio por fora quer dizer que o item não passou nesta leitura, vazio por dentro que a nota não declarou, e o texto quando há.
    public Optional<Optional<String>> de(HashDoItem hashDoItem) {
        if (hashDoItem == null) {
            return Optional.empty();
        }
        synchronized (descricoes) {
            return Optional.ofNullable(descricoes.get(hashDoItem));
        }
    }

    // Retorna quantas descrições foram registradas.
    public int quantidade() {
        return descricoes.size();
    }

    // Diz se nenhuma descrição foi registrada.
    public boolean vazio() {
        return descricoes.isEmpty();
    }
}
