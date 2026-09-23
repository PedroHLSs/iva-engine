package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

// Repositório utilizado para acessar a impressão digital do sal em uso.
interface ImpressaoDigitalDoSalJpa extends JpaRepository<ImpressaoDigitalDoSalEntidade, Short> {
}
