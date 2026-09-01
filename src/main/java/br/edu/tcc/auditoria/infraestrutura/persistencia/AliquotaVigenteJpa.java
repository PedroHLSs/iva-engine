package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso à tabela de alíquotas. */
interface AliquotaVigenteJpa extends JpaRepository<AliquotaVigenteEntidade, UUID> {

    List<AliquotaVigenteEntidade> findByCargaId(UUID cargaId);
}
