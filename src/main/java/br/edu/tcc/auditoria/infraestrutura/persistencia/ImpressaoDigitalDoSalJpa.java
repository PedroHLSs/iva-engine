package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à impressão digital do sal em uso. */
interface ImpressaoDigitalDoSalJpa extends JpaRepository<ImpressaoDigitalDoSalEntidade, Short> {
}
