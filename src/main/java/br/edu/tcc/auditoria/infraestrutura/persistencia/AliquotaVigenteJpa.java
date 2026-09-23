package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar a tabela de alíquotas.
interface AliquotaVigenteJpa extends JpaRepository<AliquotaVigenteEntidade, UUID> {

    // Busca as alíquotas de uma carga.
    List<AliquotaVigenteEntidade> findByCargaId(UUID cargaId);
}
