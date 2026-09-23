package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioNcm;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

// Repositório em memória utilizado para buscar os registros de NCM válidos numa data.
public final class RepositorioNcmEmMemoria implements RepositorioNcm {

    private final CatalogoEmMemoria<RegistroNcm> catalogo;

    // Construtor que recebe os registros e monta o catálogo; recusa datas sobrepostas para o mesmo NCM.
    public RepositorioNcmEmMemoria(Collection<RegistroNcm> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    // Busca o registro do NCM válido na data; NCM nulo devolve vazio.
    @Override
    public Optional<RegistroNcm> buscarVigenteEm(Ncm ncm, LocalDate data) {
        if (ncm == null) {
            return Optional.empty();
        }
        return catalogo.vigenteEm(ncm.valor(), data);
    }

    // Retorna quantos NCM diferentes foram carregados, sem olhar a data.
    public int quantidadeDeNcms() {
        return catalogo.quantidadeDeSeries();
    }
}
