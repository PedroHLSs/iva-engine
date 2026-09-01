package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso ao vínculo entre execução e apontamentos produzidos. */
interface AchadoDaExecucaoJpa
        extends JpaRepository<AchadoDaExecucaoEntidade, AchadoDaExecucaoEntidade.Chave> {

    List<AchadoDaExecucaoEntidade> findByExecucaoId(UUID execucaoId);
}
