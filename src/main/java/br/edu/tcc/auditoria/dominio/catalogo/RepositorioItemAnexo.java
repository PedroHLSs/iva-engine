package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;

import java.time.LocalDate;
import java.util.List;

/**
 * Porta de acesso aos vínculos entre NCM e anexo do catálogo.
 *
 * <p>Ver a nota sobre a data em {@link RepositorioClassificacaoTributaria}.</p>
 */
public interface RepositorioItemAnexo {

    /**
     * Os vínculos do NCM que valiam na data.
     *
     * <p>Devolve lista, e não {@code Optional}, porque o catálogo pode vincular
     * o mesmo NCM a mais de um anexo na mesma data. Reduzir isso a um único
     * resultado exigiria um critério de desempate que o código não tem como
     * inventar; a lista vazia significa que o catálogo nada diz.</p>
     */
    List<ItemAnexo> buscarVigentesEm(Ncm ncm, LocalDate data);
}
