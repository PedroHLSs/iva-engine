package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioAliquota;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador em memória do repositório de alíquotas.
 *
 * <p>A série é do par tributo e abrangência: abrangências diferentes convivem
 * na mesma data, o que a carga recusa é o mesmo par com vigências
 * sobrepostas.</p>
 */
public final class RepositorioAliquotaEmMemoria implements RepositorioAliquota {

    private final CatalogoEmMemoria<AliquotaVigente> catalogo;

    public RepositorioAliquotaEmMemoria(Collection<AliquotaVigente> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    @Override
    public Optional<AliquotaVigente> buscarVigenteEm(Tributo tributo, Abrangencia abrangencia, LocalDate data) {
        if (tributo == null || abrangencia == null) {
            return Optional.empty();
        }
        return catalogo.vigenteEm(AliquotaVigente.chaveDe(tributo, abrangencia), data);
    }

    @Override
    public List<AliquotaVigente> buscarVigentesEm(Tributo tributo, LocalDate data) {
        if (tributo == null) {
            return List.of();
        }
        return catalogo.vigentesEm(data).stream()
                .filter(aliquota -> aliquota.tributo() == tributo)
                .toList();
    }

    /** Quantidade de pares tributo e abrangência distintos carregados, independentemente de vigência. */
    public int quantidadeDeAbrangencias() {
        return catalogo.quantidadeDeSeries();
    }
}
