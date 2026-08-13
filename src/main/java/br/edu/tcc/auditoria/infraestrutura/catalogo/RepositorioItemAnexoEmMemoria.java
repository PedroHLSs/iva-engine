package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioItemAnexo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Adaptador em memória do repositório de vínculos entre NCM e anexo.
 *
 * <p>A série é do par NCM e anexo, então o mesmo NCM em dois anexos na mesma
 * data é carga válida, e a consulta devolve os dois. O que a carga recusa é o
 * mesmo par NCM e anexo com vigências sobrepostas.</p>
 */
public final class RepositorioItemAnexoEmMemoria implements RepositorioItemAnexo {

    private final CatalogoEmMemoria<ItemAnexo> catalogo;

    public RepositorioItemAnexoEmMemoria(Collection<ItemAnexo> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    @Override
    public List<ItemAnexo> buscarVigentesEm(Ncm ncm, LocalDate data) {
        if (ncm == null) {
            return List.of();
        }
        return catalogo.vigentesEm(data).stream()
                .filter(item -> item.ncm().equals(ncm))
                .toList();
    }

    /** Quantidade de pares NCM e anexo distintos carregados, independentemente de vigência. */
    public int quantidadeDeVinculos() {
        return catalogo.quantidadeDeSeries();
    }
}
