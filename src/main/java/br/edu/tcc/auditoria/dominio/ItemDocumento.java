package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ItemDocumentoInvalido;

import java.math.BigDecimal;
import java.util.Optional;

// Representa um item da nota com os campos de IBS/CBS como vieram. Campo que não veio é Optional vazio, e isso é diferente de zero. Valores são BigDecimal, com as casas decimais mantidas, e não são validados contra faixa.
public record ItemDocumento(
        int numeroItem,
        Optional<Ncm> ncm,
        Optional<Cfop> cfop,
        BigDecimal valorItem,
        Optional<CodigoCst> cstIbs,
        Optional<CodigoCst> cstCbs,
        Optional<CodigoClassificacaoTributaria> codigoClassificacaoTributaria,
        Optional<BigDecimal> baseCalculoIbs,
        Optional<BigDecimal> baseCalculoCbs,
        Optional<BigDecimal> aliquotaIbsUf,
        Optional<BigDecimal> aliquotaIbsMunicipal,
        Optional<BigDecimal> aliquotaCbs,
        Optional<BigDecimal> valorIbsUf,
        Optional<BigDecimal> valorIbsMunicipal,
        Optional<BigDecimal> valorCbs) {

    // Valida que o número do item seja pelo menos 1, que o valor do item exista e que nenhum campo opcional venha nulo.
    public ItemDocumento {
        if (numeroItem < 1) {
            throw new ItemDocumentoInvalido(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (valorItem == null) {
            throw new ItemDocumentoInvalido("O valor do item é obrigatório.");
        }

        exigirOptional(ncm, "ncm");
        exigirOptional(cfop, "cfop");
        exigirOptional(cstIbs, "cstIbs");
        exigirOptional(cstCbs, "cstCbs");
        exigirOptional(codigoClassificacaoTributaria, "codigoClassificacaoTributaria");
        exigirOptional(baseCalculoIbs, "baseCalculoIbs");
        exigirOptional(baseCalculoCbs, "baseCalculoCbs");
        exigirOptional(aliquotaIbsUf, "aliquotaIbsUf");
        exigirOptional(aliquotaIbsMunicipal, "aliquotaIbsMunicipal");
        exigirOptional(aliquotaCbs, "aliquotaCbs");
        exigirOptional(valorIbsUf, "valorIbsUf");
        exigirOptional(valorIbsMunicipal, "valorIbsMunicipal");
        exigirOptional(valorCbs, "valorCbs");
    }

    // Indica se o item não trouxe nenhum campo de IBS/CBS, o que é diferente de trazer tudo zerado. Ainda não é usado em produção.
    public boolean semNenhumCampoDeIbsCbs() {
        return cstIbs.isEmpty()
                && cstCbs.isEmpty()
                && codigoClassificacaoTributaria.isEmpty()
                && baseCalculoIbs.isEmpty()
                && baseCalculoCbs.isEmpty()
                && aliquotaIbsUf.isEmpty()
                && aliquotaIbsMunicipal.isEmpty()
                && aliquotaCbs.isEmpty()
                && valorIbsUf.isEmpty()
                && valorIbsMunicipal.isEmpty()
                && valorCbs.isEmpty();
    }

    // Método auxiliar para verificar se um campo opcional é nulo e lançar uma exceção.
    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new ItemDocumentoInvalido(
                    ("O campo \"%s\" deve ser Optional.empty() quando não informado, nunca nulo. "
                            + "Ausência e zero são estados distintos neste modelo.").formatted(nomeDoCampo));
        }
    }
}
