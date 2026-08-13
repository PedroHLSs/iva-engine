package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Porta de acesso aos registros de NCM do catálogo.
 *
 * <p>Ver a nota sobre a data em {@link RepositorioClassificacaoTributaria}.</p>
 */
public interface RepositorioNcm {

    /** O registro do NCM que valia na data, se algum valia. */
    Optional<RegistroNcm> buscarVigenteEm(Ncm ncm, LocalDate data);
}
