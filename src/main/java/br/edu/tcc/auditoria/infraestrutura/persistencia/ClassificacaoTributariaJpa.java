package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar a tabela de classificação tributária.
interface ClassificacaoTributariaJpa extends JpaRepository<ClassificacaoTributariaEntidade, UUID> {

    // Busca as classificações de uma carga.
    List<ClassificacaoTributariaEntidade> findByCargaId(UUID cargaId);
}
