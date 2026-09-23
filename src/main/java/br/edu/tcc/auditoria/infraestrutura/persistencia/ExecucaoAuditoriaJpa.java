package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar os recibos das execuções de auditoria.
interface ExecucaoAuditoriaJpa extends JpaRepository<ExecucaoAuditoriaEntidade, UUID> {

    // Busca as execuções da mais nova para a mais antiga, uma página por vez.
    List<ExecucaoAuditoriaEntidade> findAllByOrderByDataHoraDesc(Pageable pagina);
}
