package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioAliquota;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Repositório em memória utilizado para buscar as alíquotas válidas numa data. Abrangências diferentes do mesmo tributo convivem na mesma data; o que a carga recusa é o mesmo par tributo e abrangência com datas sobrepostas.
public final class RepositorioAliquotaEmMemoria implements RepositorioAliquota {

    private final CatalogoEmMemoria<AliquotaVigente> catalogo;

    // Construtor que recebe as alíquotas e monta o catálogo.
    public RepositorioAliquotaEmMemoria(Collection<AliquotaVigente> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    // Busca a alíquota do tributo e da abrangência válida na data; valor nulo devolve vazio.
    @Override
    public Optional<AliquotaVigente> buscarVigenteEm(Tributo tributo, Abrangencia abrangencia, LocalDate data) {
        if (tributo == null || abrangencia == null) {
            return Optional.empty();
        }
        return catalogo.vigenteEm(AliquotaVigente.chaveDe(tributo, abrangencia), data);
    }

    // Busca todas as alíquotas do tributo válidas na data, de todas as abrangências.
    @Override
    public List<AliquotaVigente> buscarVigentesEm(Tributo tributo, LocalDate data) {
        if (tributo == null) {
            return List.of();
        }
        return catalogo.vigentesEm(data).stream()
                .filter(aliquota -> aliquota.tributo() == tributo)
                .toList();
    }

    // Retorna quantos pares tributo e abrangência diferentes foram carregados, sem olhar a data.
    public int quantidadeDeAbrangencias() {
        return catalogo.quantidadeDeSeries();
    }
}
