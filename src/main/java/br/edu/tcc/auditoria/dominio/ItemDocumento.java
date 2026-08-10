package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ItemDocumentoInvalido;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Item de um documento fiscal, com os campos do grupo de IBS/CBS exatamente
 * como declarados.
 *
 * <h2>Ausente e zero são estados diferentes</h2>
 *
 * <p>Esta é a decisão central do modelo. Todo campo que pode não vir no XML é
 * {@code Optional}. Um item que não declarou base de cálculo de IBS tem
 * {@code baseCalculoIbs = Optional.empty()}; um item que declarou base zero tem
 * {@code Optional.of(BigDecimal.ZERO)}. São situações fiscalmente distintas —
 * uma é omissão de campo, a outra é informação prestada — e produzem
 * apontamentos distintos. Substituir ausência por zero apagaria a diferença e
 * faria o sistema afirmar algo que o contribuinte não declarou.</p>
 *
 * <p>Consequência prática: não existe construtor abreviado nem valor padrão.
 * Quem monta um item é obrigado pelo compilador a dizer, campo a campo, se o
 * dado veio ou não. A verbosidade é intencional.</p>
 *
 * <h2>Valores monetários</h2>
 *
 * <p>Todo valor é {@link BigDecimal}, nunca {@code double}. A escala declarada
 * é preservada como veio: para a auditoria, "0" e "0,00" são registros
 * diferentes do mesmo número. Como {@code BigDecimal.equals} distingue escala,
 * regras que comparem grandeza devem usar {@code compareTo}.</p>
 *
 * <p>Nenhum valor é validado contra faixa, sinal ou alíquota admitida: o
 * sistema audita o que foi declarado e precisa conseguir representar inclusive
 * o que está errado, sob pena de não ter o que apontar.</p>
 */
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

    /**
     * Indica se o item não trouxe nenhum campo do grupo de IBS/CBS.
     *
     * <p>Diferente de ter trazido os campos zerados: aqui não há informação
     * nenhuma a confrontar.</p>
     */
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

    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new ItemDocumentoInvalido(
                    ("O campo \"%s\" deve ser Optional.empty() quando não informado, nunca nulo. "
                            + "Ausência e zero são estados distintos neste modelo.").formatted(nomeDoCampo));
        }
    }
}
