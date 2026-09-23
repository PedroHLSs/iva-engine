package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar os vínculos entre NCM e anexo.
interface ItemAnexoJpa extends JpaRepository<ItemAnexoEntidade, UUID> {

    // Busca os vínculos de uma carga.
    List<ItemAnexoEntidade> findByCargaId(UUID cargaId);
}
