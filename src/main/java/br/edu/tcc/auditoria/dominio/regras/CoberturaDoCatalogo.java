package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Guarda o período que cada tabela carregada cobre. Dentro desse período, código que não está no catálogo vira achado na R01 e na R06; fora dele, vira NAO_AVALIADO. Desde 01/10/2026 guarda também os anexos declarados, cada um com o período em que está carregado, que a R03 1.1.0 usa; lista vazia é carga sem a declaração, como as gravadas antes dela.
public record CoberturaDoCatalogo(
        ProcedenciaNormativa classificacoesTributarias,
        ProcedenciaNormativa ncm,
        ProcedenciaNormativa itensDeAnexo,
        List<AnexoDeclarado> anexosDeclarados) {

    // Construtor com a aridade anterior a 01/10/2026: nenhum anexo declarado.
    public CoberturaDoCatalogo(
            ProcedenciaNormativa classificacoesTributarias,
            ProcedenciaNormativa ncm,
            ProcedenciaNormativa itensDeAnexo) {
        this(classificacoesTributarias, ncm, itensDeAnexo, List.of());
    }

    // Exige que as três tabelas informem o período que cobrem, e que a lista de anexos declarados exista, sem nulo nem identificador repetido.
    public CoberturaDoCatalogo {
        exigir(classificacoesTributarias, "classificações tributárias");
        exigir(ncm, "NCM");
        exigir(itensDeAnexo, "itens de anexo");
        if (anexosDeclarados == null || anexosDeclarados.stream().anyMatch(Objects::isNull)) {
            throw new RegraInvalida(
                    "A lista de anexos declarados deve ser vazia quando não há nenhum, nunca nula nem com nulo.");
        }
        Set<IdentificadorAnexo> vistos = new HashSet<>();
        for (AnexoDeclarado anexo : anexosDeclarados) {
            if (!vistos.add(anexo.identificador())) {
                throw new RegraInvalida(
                        "O anexo \"%s\" foi declarado mais de uma vez.".formatted(anexo.identificador().valor()));
            }
        }
        anexosDeclarados = List.copyOf(anexosDeclarados);
    }

    // Método auxiliar que dá erro se o período de uma tabela não foi informado.
    private static void exigir(ProcedenciaNormativa cobertura, String tabela) {
        if (cobertura == null) {
            throw new RegraInvalida(
                    "A cobertura da tabela de %s não foi declarada.".formatted(tabela));
        }
    }
}
