package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso aos itens dos documentos auditados. */
interface ItemDocumentoJpa extends JpaRepository<ItemDocumentoEntidade, UUID> {

    Optional<ItemDocumentoEntidade> findByChaveAcessoAndNumeroItem(String chaveAcesso, int numeroItem);

    /*
     * Acrescentado na Etapa 11. Buscar item a item bastava para a gravacao, que
     * percorre um documento de cada vez; a tela de conferencia de um lote pede os
     * itens de centenas de notas de uma vez, e uma consulta por item seriam
     * milhares de idas ao banco para desenhar uma tabela.
     */
    List<ItemDocumentoEntidade> findByChaveAcessoIn(Collection<String> chavesDeAcesso);
}
