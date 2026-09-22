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

/**
 * O que o documento declarou para este item, campo a campo.
 *
 * <h2>Uma lista de campos, e não trinta componentes</h2>
 *
 * <p>O item tem catorze campos que podem faltar, e cada um precisaria de um
 * companheiro dizendo por que faltou — vinte e oito componentes, vinte e oito
 * chances de esquecer o par em um deles. Aqui o par é conferido <strong>uma
 * vez</strong>, no construtor de {@link CampoDeclarado}, e vale para todos.</p>
 *
 * <h2>Ausente e zero continuam sendo coisas diferentes até a tela</h2>
 *
 * <p>É a decisão central do modelo desde a Etapa 1, e é aqui que ela costuma
 * morrer: um campo que não veio no XML sai com valor nulo e o motivo ao lado,
 * nunca com {@code "0"} e nunca com traço. Omitir base de cálculo e declarar base
 * zero são fatos fiscais distintos, e é sobre a diferença entre eles que várias
 * das regras apontam.</p>
 */
public record DeclaracaoExposta(int numeroItem, List<CampoDeclarado> campos) {

    static final String NAO_DECLARADO = "o documento não declarou este campo para o item";

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

    private static <T> CampoDeclarado opcional(
            String campo, Optional<T> valor, Function<T, String> comoTexto) {
        return new CampoDeclarado(
                campo,
                valor.map(comoTexto).orElse(null),
                valor.isPresent() ? null : NAO_DECLARADO);
    }

    /** Número com a escala declarada preservada, como texto — mesma razão da D009. */
    private static CampoDeclarado monetario(String campo, Optional<BigDecimal> valor) {
        return opcional(campo, valor, BigDecimal::toPlainString);
    }

    /** Um campo do item: o que veio, ou o motivo de não ter vindo. */
    public record CampoDeclarado(String campo, String valor, String motivoDaAusencia) {

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
