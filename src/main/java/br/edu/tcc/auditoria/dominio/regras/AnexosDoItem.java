package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;

import java.util.Comparator;
import java.util.List;

/**
 * Leitura ordenada dos anexos a que o catálogo vincula um NCM.
 *
 * <p>Um NCM pode estar vinculado a mais de um anexo na mesma data — o catálogo
 * admite isso de propósito, porque afirmar o contrário seria afirmar sobre a
 * norma. Como consequência, as regras que leem esses vínculos precisam de uma
 * ordem estável: a lista devolvida pelo repositório reflete a ordem de carga, e
 * duas cargas do mesmo conteúdo em ordem diferente produziriam evidências em
 * sequência diferente. Ordenar pelo identificador do anexo elimina isso.</p>
 */
final class AnexosDoItem {

    static final String TABELA = "catalogo:itemAnexo";

    private AnexosDoItem() {
    }

    /** Vínculos vigentes do NCM, em ordem de identificador do anexo. */
    static List<ItemAnexo> ordenados(ContextoNormativo contexto, Ncm ncm) {
        return contexto.anexosDoNcm(ncm).stream()
                .sorted(Comparator.comparing(anexo -> anexo.identificadorDoAnexo().valor()))
                .toList();
    }

    /** Identificadores dos anexos vigentes do NCM, em ordem. */
    static List<String> identificadoresOrdenados(ContextoNormativo contexto, Ncm ncm) {
        return ordenados(contexto, ncm).stream()
                .map(anexo -> anexo.identificadorDoAnexo().valor())
                .toList();
    }
}
