package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar os arquivos que uma análise não conseguiu ler.
interface FalhaDeLeituraDaExecucaoJpa
        extends JpaRepository<FalhaDeLeituraDaExecucaoEntidade, UUID> {

    // Busca os arquivos ilegíveis de uma execução, na ordem em que falharam.
    List<FalhaDeLeituraDaExecucaoEntidade> findByExecucaoIdOrderByOrdemAsc(UUID execucaoId);
}
