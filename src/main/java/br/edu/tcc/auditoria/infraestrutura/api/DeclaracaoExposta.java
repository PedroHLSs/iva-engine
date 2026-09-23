package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.dominio.Cfop;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// Representa o que a nota declarou para o item, campo a campo. Campo que não veio sai com valor null e o motivo ao lado, nunca como "0": não declarar a base e declarar base zero são coisas diferentes.
public record DeclaracaoExposta(int numeroItem, List<CampoDeclarado> campos) {

    // Motivo escrito no lugar de um campo que a nota não trouxe.
    static final String NAO_DECLARADO = "o documento não declarou este campo para o item";

    // Valida que o número do item seja pelo menos 1 e que haja campos.
    public DeclaracaoExposta {
        if (numeroItem < 1) {
            throw new RespostaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (campos == null || campos.isEmpty()) {
            throw new RespostaInvalida(
                    "A declaração precisa dos campos do item: vazia, a tela mostraria um produto sem "
                            + "nada declarado, que é outra coisa.");
        }
        campos = List.copyOf(campos);
    }

    // Método estático que monta a declaração com os catorze campos do item, na ordem da tela.
    static DeclaracaoExposta de(ItemDocumento item) {
        List<CampoDeclarado> campos = new ArrayList<>();
        campos.add(opcional("NCM", item.ncm(), Ncm::valor));
        campos.add(opcional("CFOP", item.cfop(), Cfop::valor));
        campos.add(new CampoDeclarado(
                "Valor do produto", item.valorItem().toPlainString(), null));
        campos.add(opcional("CST do IBS", item.cstIbs(), CodigoCst::valor));
        campos.add(opcional("CST da CBS", item.cstCbs(), CodigoCst::valor));
        campos.add(opcional(
                "cClassTrib",
                item.codigoClassificacaoTributaria(),
                CodigoClassificacaoTributaria::valor));
        campos.add(monetario("Base de cálculo do IBS", item.baseCalculoIbs()));
        campos.add(monetario("Base de cálculo da CBS", item.baseCalculoCbs()));
        campos.add(monetario("Alíquota do IBS - parcela estadual", item.aliquotaIbsUf()));
        campos.add(monetario("Alíquota do IBS - parcela municipal", item.aliquotaIbsMunicipal()));
        campos.add(monetario("Alíquota da CBS", item.aliquotaCbs()));
        campos.add(monetario("Valor do IBS - parcela estadual", item.valorIbsUf()));
        campos.add(monetario("Valor do IBS - parcela municipal", item.valorIbsMunicipal()));
        campos.add(monetario("Valor da CBS", item.valorCbs()));
        return new DeclaracaoExposta(item.numeroItem(), campos);
    }

    // Método auxiliar que cria um campo com o valor em texto, ou com o motivo de não ter vindo.
    private static <T> CampoDeclarado opcional(
            String campo, Optional<T> valor, Function<T, String> comoTexto) {
        return new CampoDeclarado(
                campo,
                valor.map(comoTexto).orElse(null),
                valor.isPresent() ? null : NAO_DECLARADO);
    }

    // Método auxiliar para campo numérico: o número vai como texto, com as casas decimais que vieram na nota.
    private static CampoDeclarado monetario(String campo, Optional<BigDecimal> valor) {
        return opcional(campo, valor, BigDecimal::toPlainString);
    }

    // Representa um campo do item: o que veio, ou o motivo de não ter vindo.
    public record CampoDeclarado(String campo, String valor, String motivoDaAusencia) {

        // Valida que o campo tenha nome e exatamente um dos dois: valor ou motivo.
        public CampoDeclarado {
            if (campo == null || campo.isBlank()) {
                throw new RespostaInvalida("O campo declarado precisa de nome.");
            }
            if ((valor == null) == (motivoDaAusencia == null)) {
                throw new RespostaInvalida(
                        ("O campo \"%s\" precisa ou do valor, ou do motivo de não ter vindo — "
                                + "exatamente um dos dois. Célula em branco na tela vira zero na "
                                + "cabeça de quem lê.").formatted(campo));
            }
        }
    }
}
