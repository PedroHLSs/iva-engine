package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar os anexos declarados de cada carga (V16).
interface AnexoDeclaradoJpa extends JpaRepository<AnexoDeclaradoEntidade, AnexoDeclaradoEntidade.Chave> {

    // Busca os anexos declarados de uma carga.
    List<AnexoDeclaradoEntidade> findByCargaId(UUID cargaId);
}
