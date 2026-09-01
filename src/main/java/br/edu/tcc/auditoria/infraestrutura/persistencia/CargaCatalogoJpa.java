package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Acesso às cargas de catálogo importadas. */
interface CargaCatalogoJpa extends JpaRepository<CargaCatalogoEntidade, UUID> {

    /** A carga mais recente; a versão desempata cargas importadas no mesmo instante. */
    Optional<CargaCatalogoEntidade> findTopByOrderByImportadoEmDescVersaoDesc();

    boolean existsByVersao(String versao);
}
