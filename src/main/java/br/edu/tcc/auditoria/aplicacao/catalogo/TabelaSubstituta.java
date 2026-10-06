package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.util.List;
import java.util.Optional;

// Representa uma tabela que chegou num CSV novo para substituir a da carga: os registros e a natureza declarada no arquivo.
public record TabelaSubstituta<T>(List<T> registros, Optional<Natureza> natureza) {

    // Valida a lista e exige natureza exatamente quando há registro, como na importação.
    public TabelaSubstituta {
        if (registros == null || natureza == null) {
            throw new CatalogoInvalido("A tabela substituta precisa de registros e natureza, nunca nulos.");
        }
        if (registros.isEmpty() != natureza.isEmpty()) {
            throw new CatalogoInvalido(
                    "A tabela substituta traz natureza se, e somente se, traz registro.");
        }
        registros = List.copyOf(registros);
    }
}
