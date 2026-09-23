package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;

import java.time.LocalDate;
import java.util.Optional;

// Interface responsável por buscar os registros de NCM do catálogo numa data.
public interface RepositorioNcm {

    // Retorna o registro do NCM que valia na data, se algum valia.
    Optional<RegistroNcm> buscarVigenteEm(Ncm ncm, LocalDate data);
}
