package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso aos documentos auditados, chaveados pela chave de acesso. */
interface DocumentoJpa extends JpaRepository<DocumentoEntidade, String> {
}
