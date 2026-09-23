package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar a cobertura declarada de cada carga.
interface CoberturaCatalogoJpa
        extends JpaRepository<CoberturaCatalogoEntidade, CoberturaCatalogoEntidade.Chave> {

    // Busca a cobertura de uma carga.
    List<CoberturaCatalogoEntidade> findByCargaId(UUID cargaId);
}
