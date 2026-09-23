package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

// Repositório utilizado para acessar os documentos auditados, pela chave de acesso.
interface DocumentoJpa extends JpaRepository<DocumentoEntidade, String> {
}
