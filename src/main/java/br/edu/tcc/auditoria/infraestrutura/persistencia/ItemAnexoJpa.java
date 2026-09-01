package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso à tabela de vínculo entre NCM e anexo. */
interface ItemAnexoJpa extends JpaRepository<ItemAnexoEntidade, UUID> {

    List<ItemAnexoEntidade> findByCargaId(UUID cargaId);
}
