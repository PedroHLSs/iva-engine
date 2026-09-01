package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso à tabela de classificação tributária. */
interface ClassificacaoTributariaJpa extends JpaRepository<ClassificacaoTributariaEntidade, UUID> {

    List<ClassificacaoTributariaEntidade> findByCargaId(UUID cargaId);
}
