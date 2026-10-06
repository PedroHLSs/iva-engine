package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Repositório utilizado para acessar a marca de leitura registrada de cada execução (V20, D018).
interface LeituraDaExecucaoJpa extends JpaRepository<LeituraDaExecucaoEntidade, UUID> {
}
