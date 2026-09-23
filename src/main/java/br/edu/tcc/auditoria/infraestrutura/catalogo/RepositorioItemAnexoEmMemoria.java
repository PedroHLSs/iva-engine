package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioItemAnexo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

// Repositório em memória utilizado para buscar os vínculos entre NCM e anexo válidos numa data. O mesmo NCM pode estar em dois anexos na mesma data; o que a carga recusa é o mesmo par NCM e anexo com datas sobrepostas.
public final class RepositorioItemAnexoEmMemoria implements RepositorioItemAnexo {

    private final CatalogoEmMemoria<ItemAnexo> catalogo;

    // Construtor que recebe os vínculos e monta o catálogo.
    public RepositorioItemAnexoEmMemoria(Collection<ItemAnexo> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    // Busca todos os anexos do NCM válidos na data; NCM nulo devolve lista vazia.
    @Override
    public List<ItemAnexo> buscarVigentesEm(Ncm ncm, LocalDate data) {
        if (ncm == null) {
            return List.of();
        }
        return catalogo.vigentesEm(data).stream()
                .filter(item -> item.ncm().equals(ncm))
                .toList();
    }

    // Retorna quantos pares NCM e anexo diferentes foram carregados, sem olhar a data.
    public int quantidadeDeVinculos() {
        return catalogo.quantidadeDeSeries();
    }
}
