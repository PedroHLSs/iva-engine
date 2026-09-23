package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar as avaliações que não concluíram, por execução.
interface AvaliacaoNaoConcluidaJpa extends JpaRepository<AvaliacaoNaoConcluidaEntidade, UUID> {

    // Busca as não concluídas de uma execução, ordenadas por chave, item e regra.
    List<AvaliacaoNaoConcluidaEntidade> findByExecucaoIdOrderByChaveAcessoAscNumeroItemAscRegraIdAsc(
            UUID execucaoId);
}
