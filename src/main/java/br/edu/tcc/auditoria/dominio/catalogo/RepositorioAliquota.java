package br.edu.tcc.auditoria.dominio.catalogo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Porta de acesso às alíquotas do catálogo.
 *
 * <p>Ver a nota sobre a data em {@link RepositorioClassificacaoTributaria}.</p>
 */
public interface RepositorioAliquota {

    /** A alíquota do par tributo e abrangência que valia na data, se alguma valia. */
    Optional<AliquotaVigente> buscarVigenteEm(Tributo tributo, Abrangencia abrangencia, LocalDate data);

    /** Todas as alíquotas do tributo que valiam na data, em qualquer abrangência. */
    List<AliquotaVigente> buscarVigentesEm(Tributo tributo, LocalDate data);
}
