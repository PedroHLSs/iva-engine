package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso às avaliações que não concluíram, por execução. */
interface AvaliacaoNaoConcluidaJpa extends JpaRepository<AvaliacaoNaoConcluidaEntidade, UUID> {

    List<AvaliacaoNaoConcluidaEntidade> findByExecucaoIdOrderByChaveAcessoAscNumeroItemAscRegraIdAsc(
            UUID execucaoId);
}
