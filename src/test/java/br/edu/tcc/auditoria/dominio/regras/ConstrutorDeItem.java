package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Cfop;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Construtor de {@link ItemDocumento} para os testes de regra.
 *
 * <p><strong>Só existe em código de teste.</strong> O modelo de produção recusa
 * construtor abreviado de propósito (decisão D002): quem monta um item de
 * verdade é obrigado pelo compilador a dizer, campo a campo, se o dado veio. Um
 * teste de regra, porém, mexe em dois ou três campos e precisa que os outros
 * doze não atrapalhem a leitura do cenário.</p>
 *
 * <p>O padrão de todo campo opcional é {@code Optional.empty()} — isto é,
 * <em>não veio</em>. Essa escolha é o que impede o construtor de reintroduzir
 * pela porta dos fundos o problema que D002 fecha: nenhum campo aparece
 * preenchido sem que o teste tenha pedido, e nenhum zero surge de padrão.</p>
 *
 * <p>Todos os valores aceitos são texto e viram {@link BigDecimal} sem passar
 * por {@code double}, preservando a escala escrita no teste.</p>
 */
public final class ConstrutorDeItem {

    private int numeroItem = 1;
    private BigDecimal valorItem = new BigDecimal("99.99");
    private Optional<Ncm> ncm = Optional.empty();
    private Optional<Cfop> cfop = Optional.empty();
    private Optional<CodigoCst> cstIbs = Optional.empty();
    private Optional<CodigoCst> cstCbs = Optional.empty();
    private Optional<CodigoClassificacaoTributaria> classificacao = Optional.empty();
    private Optional<BigDecimal> baseCalculoIbs = Optional.empty();
    private Optional<BigDecimal> baseCalculoCbs = Optional.empty();
    private Optional<BigDecimal> aliquotaIbsUf = Optional.empty();
    private Optional<BigDecimal> aliquotaIbsMunicipal = Optional.empty();
    private Optional<BigDecimal> aliquotaCbs = Optional.empty();
    private Optional<BigDecimal> valorIbsUf = Optional.empty();
    private Optional<BigDecimal> valorIbsMunicipal = Optional.empty();
    private Optional<BigDecimal> valorCbs = Optional.empty();

    private ConstrutorDeItem() {
    }

    public static ConstrutorDeItem item() {
        return new ConstrutorDeItem();
    }

    public ConstrutorDeItem numero(int numeroItem) {
        this.numeroItem = numeroItem;
        return this;
    }

    public ConstrutorDeItem valorItem(String valor) {
        this.valorItem = new BigDecimal(valor);
        return this;
    }

    public ConstrutorDeItem ncm(String valor) {
        this.ncm = Optional.of(new Ncm(valor));
        return this;
    }

    public ConstrutorDeItem cfop(String valor) {
        this.cfop = Optional.of(new Cfop(valor));
        return this;
    }

    public ConstrutorDeItem cstIbs(String valor) {
        this.cstIbs = Optional.of(new CodigoCst(valor));
        return this;
    }

    public ConstrutorDeItem cstCbs(String valor) {
        this.cstCbs = Optional.of(new CodigoCst(valor));
        return this;
    }

    public ConstrutorDeItem classificacao(String valor) {
        this.classificacao = Optional.of(new CodigoClassificacaoTributaria(valor));
        return this;
    }

    public ConstrutorDeItem baseCalculoIbs(String valor) {
        this.baseCalculoIbs = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem baseCalculoCbs(String valor) {
        this.baseCalculoCbs = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem aliquotaIbsUf(String valor) {
        this.aliquotaIbsUf = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem aliquotaIbsMunicipal(String valor) {
        this.aliquotaIbsMunicipal = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem aliquotaCbs(String valor) {
        this.aliquotaCbs = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem valorIbsUf(String valor) {
        this.valorIbsUf = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem valorIbsMunicipal(String valor) {
        this.valorIbsMunicipal = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ConstrutorDeItem valorCbs(String valor) {
        this.valorCbs = Optional.of(new BigDecimal(valor));
        return this;
    }

    public ItemDocumento construir() {
        return new ItemDocumento(
                numeroItem,
                ncm,
                cfop,
                valorItem,
                cstIbs,
                cstCbs,
                classificacao,
                baseCalculoIbs,
                baseCalculoCbs,
                aliquotaIbsUf,
                aliquotaIbsMunicipal,
                aliquotaCbs,
                valorIbsUf,
                valorIbsMunicipal,
                valorCbs);
    }
}
