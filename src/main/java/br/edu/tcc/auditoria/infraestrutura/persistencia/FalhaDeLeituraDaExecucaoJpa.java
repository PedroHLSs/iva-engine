package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FalhaDeLeituraDaExecucaoJpa
        extends JpaRepository<FalhaDeLeituraDaExecucaoEntidade, UUID> {

    List<FalhaDeLeituraDaExecucaoEntidade> findByExecucaoIdOrderByOrdemAsc(UUID execucaoId);
}
