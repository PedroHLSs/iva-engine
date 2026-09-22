package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Acesso às cargas de catálogo importadas. */
interface CargaCatalogoJpa extends JpaRepository<CargaCatalogoEntidade, UUID> {

    /** A carga mais recente; a versão desempata cargas importadas no mesmo instante. */
    Optional<CargaCatalogoEntidade> findTopByOrderByImportadoEmDescVersaoDesc();

    boolean existsByVersao(String versao);

    /*
     * Acrescentado pela etapa de conferência, sobre a Etapa 5.
     *
     * A versão é chave única em carga_catalogo desde a V1, então buscar por ela
     * devolve no máximo uma linha. É o que permite reabrir uma análise e resolver
     * o tratamento contra a carga que ela registrou, e não contra a de hoje.
     */
    Optional<CargaCatalogoEntidade> findByVersao(String versao);
}
