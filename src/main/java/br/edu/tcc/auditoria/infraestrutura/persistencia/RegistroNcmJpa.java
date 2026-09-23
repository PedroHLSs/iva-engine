package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar a tabela de NCM.
interface RegistroNcmJpa extends JpaRepository<RegistroNcmEntidade, UUID> {

    // Busca os registros de NCM de uma carga.
    List<RegistroNcmEntidade> findByCargaId(UUID cargaId);
}
