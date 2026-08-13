package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * O que o catálogo diz sobre um código de classificação tributária numa dada
 * vigência.
 *
 * <p>Nenhum código, CST, dispositivo ou percentual está escrito aqui: todos
 * chegam por importação. Este tipo apenas dá forma ao que foi importado, para
 * que as regras consigam consultá-lo.</p>
 *
 * <p>{@code cstsCompativeis} pode vir vazio, e o construtor aceita. Afirmar que
 * toda classificação tem ao menos um CST compatível seria afirmação sobre a
 * norma, e o código não a faz — cabe às regras decidirem o que fazer com um
 * conjunto vazio.</p>
 *
 * @param codigo                          o {@code cClassTrib} a que este registro se refere
 * @param cstsCompativeis                 CSTs que o catálogo admite junto deste código
 * @param dispositivoLegal                dispositivo citado pela fonte para este código
 * @param indicadorDeBeneficio            se o catálogo marca este código como benefício
 * @param percentualReducao               redução declarada, quando o catálogo traz uma
 * @param camposObrigatoriosCondicionados nomes dos campos que passam a ser exigidos neste código
 * @param procedencia                     vigência e fonte
 */
public record ClassificacaoTributaria(
        CodigoClassificacaoTributaria codigo,
        Set<CodigoCst> cstsCompativeis,
        String dispositivoLegal,
        boolean indicadorDeBeneficio,
        Optional<BigDecimal> percentualReducao,
        List<String> camposObrigatoriosCondicionados,
        ProcedenciaNormativa procedencia) implements RegistroNormativo {

    public ClassificacaoTributaria {
        if (codigo == null) {
            throw new RegistroNormativoInvalido("A classificação tributária precisa de código.");
        }
        if (procedencia == null) {
            throw new RegistroNormativoInvalido(
                    "A classificação tributária \"%s\" precisa de vigência e fonte normativa."
                            .formatted(codigo.valor()));
        }
        if (dispositivoLegal == null || dispositivoLegal.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "A classificação tributária \"%s\" precisa citar o dispositivo legal."
                            .formatted(codigo.valor()));
        }
        if (cstsCompativeis == null) {
            throw new RegistroNormativoInvalido(
                    "O conjunto de CSTs compatíveis de \"%s\" deve ser vazio quando não há nenhum, nunca nulo."
                            .formatted(codigo.valor()));
        }
        if (cstsCompativeis.stream().anyMatch(Objects::isNull)) {
            throw new RegistroNormativoInvalido(
                    "O conjunto de CSTs compatíveis de \"%s\" não pode conter elemento nulo."
                            .formatted(codigo.valor()));
        }
        if (percentualReducao == null) {
            throw new RegistroNormativoInvalido(
                    ("A redução de \"%s\" deve ser Optional.empty() quando o catálogo não traz nenhuma, "
                            + "nunca nula e nunca zero: não declarar redução e declarar redução de zero "
                            + "são coisas diferentes.").formatted(codigo.valor()));
        }
        if (camposObrigatoriosCondicionados == null) {
            throw new RegistroNormativoInvalido(
                    "A lista de campos obrigatórios condicionados de \"%s\" deve ser vazia quando não há "
                            + "nenhum, nunca nula.".formatted(codigo.valor()));
        }
        for (String campo : camposObrigatoriosCondicionados) {
            if (campo == null || campo.isBlank()) {
                throw new RegistroNormativoInvalido(
                        "A lista de campos obrigatórios condicionados de \"%s\" tem entrada vazia."
                                .formatted(codigo.valor()));
            }
        }

        cstsCompativeis = Set.copyOf(new LinkedHashSet<>(cstsCompativeis));
        camposObrigatoriosCondicionados = List.copyOf(camposObrigatoriosCondicionados);
    }

    @Override
    public String chaveDeVigencia() {
        return codigo.valor();
    }

    /** Indica se o catálogo admite este CST junto deste código, na vigência deste registro. */
    public boolean admiteCst(CodigoCst cst) {
        return cstsCompativeis.contains(cst);
    }
}
