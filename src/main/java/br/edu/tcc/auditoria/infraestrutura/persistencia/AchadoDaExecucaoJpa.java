package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar o vínculo entre cada execução e os apontamentos que ela produziu.
interface AchadoDaExecucaoJpa
        extends JpaRepository<AchadoDaExecucaoEntidade, AchadoDaExecucaoEntidade.Chave> {

    // Busca os vínculos de uma execução.
    List<AchadoDaExecucaoEntidade> findByExecucaoId(UUID execucaoId);
}
