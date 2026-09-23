package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar os itens lidos em cada execução.
interface ItemDaExecucaoJpa extends JpaRepository<ItemDaExecucaoEntidade, UUID> {

    // Busca os itens de uma execução, ordenados por chave de acesso e número do item.
    List<ItemDaExecucaoEntidade> findByExecucaoIdOrderByChaveAcessoAscNumeroItemAsc(UUID execucaoId);
}
