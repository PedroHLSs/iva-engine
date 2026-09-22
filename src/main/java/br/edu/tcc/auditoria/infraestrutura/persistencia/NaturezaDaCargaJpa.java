package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acesso à procedência declarada de cada carga. */
interface NaturezaDaCargaJpa
        extends JpaRepository<NaturezaDaCargaEntidade, NaturezaDaCargaEntidade.Chave> {

    List<NaturezaDaCargaEntidade> findByCargaId(UUID cargaId);
}
