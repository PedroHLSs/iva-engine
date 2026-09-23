package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

// Guarda o período que cada tabela carregada cobre. Dentro desse período, código que não está no catálogo vira achado na R01 e na R06; fora dele, vira NAO_AVALIADO.
public record CoberturaDoCatalogo(
        ProcedenciaNormativa classificacoesTributarias,
        ProcedenciaNormativa ncm,
        ProcedenciaNormativa itensDeAnexo) {

    // Exige que as três tabelas informem o período que cobrem.
    public CoberturaDoCatalogo {
        exigir(classificacoesTributarias, "classificações tributárias");
        exigir(ncm, "NCM");
        exigir(itensDeAnexo, "itens de anexo");
    }

    // Método auxiliar que dá erro se o período de uma tabela não foi informado.
    private static void exigir(ProcedenciaNormativa cobertura, String tabela) {
        if (cobertura == null) {
            throw new RegraInvalida(
                    "A cobertura da tabela de %s não foi declarada.".formatted(tabela));
        }
    }
}
