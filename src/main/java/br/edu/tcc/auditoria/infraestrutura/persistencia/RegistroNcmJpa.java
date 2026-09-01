package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso à tabela de NCM. */
interface RegistroNcmJpa extends JpaRepository<RegistroNcmEntidade, UUID> {

    List<RegistroNcmEntidade> findByCargaId(UUID cargaId);
}
