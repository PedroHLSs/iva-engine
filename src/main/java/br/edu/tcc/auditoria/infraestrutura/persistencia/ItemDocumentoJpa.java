package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Repositório utilizado para acessar os itens dos documentos auditados.
interface ItemDocumentoJpa extends JpaRepository<ItemDocumentoEntidade, UUID> {

    // Busca um item pela chave de acesso e pelo número.
    Optional<ItemDocumentoEntidade> findByChaveAcessoAndNumeroItem(String chaveAcesso, int numeroItem);

    // Busca os itens de várias notas de uma vez, para a tela do lote não fazer uma consulta por item. Acrescentado na Etapa 11.
    List<ItemDocumentoEntidade> findByChaveAcessoIn(Collection<String> chavesDeAcesso);
}
