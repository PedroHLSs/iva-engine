package br.edu.tcc.auditoria.dominio.catalogo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Interface responsável por buscar as alíquotas do catálogo numa data; as regras não a usam diretamente, e sim o ContextoNormativo.
public interface RepositorioAliquota {

    // Retorna a alíquota do par tributo e abrangência que valia na data, se alguma valia.
    Optional<AliquotaVigente> buscarVigenteEm(Tributo tributo, Abrangencia abrangencia, LocalDate data);

    // Retorna todas as alíquotas do tributo que valiam na data, em qualquer abrangência.
    List<AliquotaVigente> buscarVigentesEm(Tributo tributo, LocalDate data);
}
