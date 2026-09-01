package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso aos recibos de execução de auditoria. */
interface ExecucaoAuditoriaJpa extends JpaRepository<ExecucaoAuditoriaEntidade, UUID> {

    List<ExecucaoAuditoriaEntidade> findAllByOrderByDataHoraDesc(Pageable pagina);
}
