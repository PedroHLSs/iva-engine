package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso à cobertura declarada de cada carga. */
interface CoberturaCatalogoJpa
        extends JpaRepository<CoberturaCatalogoEntidade, CoberturaCatalogoEntidade.Chave> {

    List<CoberturaCatalogoEntidade> findByCargaId(UUID cargaId);
}
