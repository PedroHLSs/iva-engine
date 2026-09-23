package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioClassificacaoTributaria;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

// Repositório em memória utilizado para buscar as classificações tributárias válidas numa data. Duas versões do mesmo código com datas sobrepostas são recusadas na carga, pelo SerieNormativa do domínio.
public final class RepositorioClassificacaoTributariaEmMemoria implements RepositorioClassificacaoTributaria {

    private final CatalogoEmMemoria<ClassificacaoTributaria> catalogo;

    // Construtor que recebe as classificações e monta o catálogo.
    public RepositorioClassificacaoTributariaEmMemoria(Collection<ClassificacaoTributaria> registros) {
        this.catalogo = new CatalogoEmMemoria<>(registros);
    }

    // Busca a classificação do código válida na data; código nulo devolve vazio.
    @Override
    public Optional<ClassificacaoTributaria> buscarVigenteEm(CodigoClassificacaoTributaria codigo, LocalDate data) {
        if (codigo == null) {
            return Optional.empty();
        }
        return catalogo.vigenteEm(codigo.valor(), data);
    }

    // Retorna quantos códigos diferentes foram carregados, sem olhar a data.
    public int quantidadeDeCodigos() {
        return catalogo.quantidadeDeSeries();
    }
}
