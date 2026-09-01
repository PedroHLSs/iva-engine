package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Acesso aos itens dos documentos auditados. */
interface ItemDocumentoJpa extends JpaRepository<ItemDocumentoEntidade, UUID> {

    Optional<ItemDocumentoEntidade> findByChaveAcessoAndNumeroItem(String chaveAcesso, int numeroItem);
}
