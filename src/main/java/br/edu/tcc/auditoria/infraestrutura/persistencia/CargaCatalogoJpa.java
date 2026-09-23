package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Repositório utilizado para acessar as cargas de catálogo importadas.
interface CargaCatalogoJpa extends JpaRepository<CargaCatalogoEntidade, UUID> {

    // Busca a carga mais recente; a versão desempata cargas importadas no mesmo instante.
    Optional<CargaCatalogoEntidade> findTopByOrderByImportadoEmDescVersaoDesc();

    // Diz se já existe carga com essa versão.
    boolean existsByVersao(String versao);

    // Busca a carga pela versão, para reabrir uma análise com a carga que ela usou, e não com a de hoje. Acrescentado na Etapa 11.
    Optional<CargaCatalogoEntidade> findByVersao(String versao);
}
