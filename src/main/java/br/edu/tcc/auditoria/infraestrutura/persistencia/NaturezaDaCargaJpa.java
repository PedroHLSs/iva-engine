package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar a procedência declarada de cada carga.
interface NaturezaDaCargaJpa
        extends JpaRepository<NaturezaDaCargaEntidade, NaturezaDaCargaEntidade.Chave> {

    // Busca a procedência de uma carga, tabela por tabela.
    List<NaturezaDaCargaEntidade> findByCargaId(UUID cargaId);
}
