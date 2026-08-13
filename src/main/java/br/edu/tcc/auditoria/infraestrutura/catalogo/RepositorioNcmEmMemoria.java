package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioNcm;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

/** Adaptador em memória do repositório de registros de NCM. */
public final class RepositorioNcmEmMemoria implements RepositorioNcm {

    private final CatalogoEmMemoria<RegistroNcm> catalogo;

    public RepositorioNcmEmMemoria(Collection<RegistroNcm> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    @Override
    public Optional<RegistroNcm> buscarVigenteEm(Ncm ncm, LocalDate data) {
        if (ncm == null) {
            return Optional.empty();
        }
        return catalogo.vigenteEm(ncm.valor(), data);
    }

    /** Quantidade de NCM distintos carregados, independentemente de vigência. */
    public int quantidadeDeNcms() {
        return catalogo.quantidadeDeSeries();
    }
}
