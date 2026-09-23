package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;

import java.util.Comparator;
import java.util.List;

// Busca os anexos em que o catálogo coloca um NCM e devolve sempre na mesma ordem, porque um NCM pode estar em mais de um anexo.
final class AnexosDoItem {

    static final String TABELA = "catalogo:itemAnexo";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private AnexosDoItem() {
    }

    // Devolve os anexos válidos do NCM na data, ordenados pela identificação do anexo.
    static List<ItemAnexo> ordenados(ContextoNormativo contexto, Ncm ncm) {
        return contexto.anexosDoNcm(ncm).stream()
                .sorted(Comparator.comparing(anexo -> anexo.identificadorDoAnexo().valor()))
                .toList();
    }

    // Devolve só as identificações dos anexos válidos do NCM, em ordem.
    static List<String> identificadoresOrdenados(ContextoNormativo contexto, Ncm ncm) {
        return ordenados(contexto, ncm).stream()
                .map(anexo -> anexo.identificadorDoAnexo().valor())
                .toList();
    }
}
