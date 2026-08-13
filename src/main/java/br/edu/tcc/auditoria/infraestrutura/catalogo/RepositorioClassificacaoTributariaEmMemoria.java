package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioClassificacaoTributaria;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

/**
 * Adaptador em memória do repositório de classificações tributárias.
 *
 * <p>Rejeita na carga duas versões do mesmo código com vigências sobrepostas —
 * a recusa vem de {@code SerieNormativa}, no domínio.</p>
 */
public final class RepositorioClassificacaoTributariaEmMemoria implements RepositorioClassificacaoTributaria {

    private final CatalogoEmMemoria<ClassificacaoTributaria> catalogo;

    public RepositorioClassificacaoTributariaEmMemoria(Collection<ClassificacaoTributaria> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    @Override
    public Optional<ClassificacaoTributaria> buscarVigenteEm(CodigoClassificacaoTributaria codigo, LocalDate data) {
        if (codigo == null) {
            return Optional.empty();
        }
        return catalogo.vigenteEm(codigo.valor(), data);
    }

    /** Quantidade de códigos distintos carregados, independentemente de vigência. */
    public int quantidadeDeCodigos() {
        return catalogo.quantidadeDeSeries();
    }
}
