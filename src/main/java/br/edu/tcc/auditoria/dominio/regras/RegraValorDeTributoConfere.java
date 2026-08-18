package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * R05 — o valor de tributo declarado no item confere com a base declarada
 * multiplicada pela alíquota vigente na data, dentro da tolerância recebida?
 *
 * <p>Confronta três pares de uma vez, na ordem fixa IBS estadual, IBS municipal
 * e CBS. Cada par usa a base que o próprio item declarou e a alíquota que o
 * catálogo traz — não a alíquota declarada no documento. Conferir o declarado
 * contra ele mesmo só detectaria erro de aritmética; o que interessa é confrontar
 * o declarado com a referência.</p>
 *
 * <p>Severidade grave: é a regra do conjunto que mexe com valor.</p>
 *
 * <h2>Convenção de unidade</h2>
 *
 * <p>O percentual do catálogo é lido como porcentagem, de modo que o valor
 * esperado é {@code base × percentual ÷ 100}. Isso é convenção de unidade da
 * coluna importada, não afirmação sobre a norma, e está dita aqui para que quem
 * prepara o CSV saiba em que escala preencher.</p>
 *
 * <h2>Escolha da alíquota</h2>
 *
 * <p>O catálogo guarda alíquotas por par tributo e abrangência, e a regra não
 * sabe qual abrangência se aplica a qual operação — isso é conteúdo normativo, e
 * escolher uma seria inventá-lo. Por isso a regra só calcula quando o catálogo
 * traz exatamente uma alíquota vigente para o tributo na data. Havendo mais de
 * uma, o resultado é {@code NAO_AVALIADO} nomeando as abrangências encontradas,
 * e cabe a quem opera o catálogo restringir a carga ou a quem evoluir o sistema
 * modelar a escolha de abrangência.</p>
 *
 * <h2>Como os três pares se combinam num resultado só</h2>
 *
 * <ul>
 *   <li>Qualquer divergência acima da tolerância produz achado, ainda que outro
 *       par não tenha sido avaliável: a divergência encontrada é fato.</li>
 *   <li>Sem divergência, se algum par ficou sem avaliar, o resultado é
 *       {@code NAO_AVALIADO} com todos os motivos. Dizer conforme aqui seria
 *       afirmar que se conferiu o que não se conferiu.</li>
 *   <li>{@code CONFORME} só quando os três pares avaliáveis fecharam e nenhum
 *       ficou pendente.</li>
 * </ul>
 */
public final class RegraValorDeTributoConfere extends RegraDeItem {

    public static final String ID = "R05";
    public static final String VERSAO = "1.0.0";

    static final String TABELA = "catalogo:aliquota";

    private static final int CASAS_DA_PORCENTAGEM = 2;

    private final ToleranciaDeValor tolerancia;

    public RegraValorDeTributoConfere(ToleranciaDeValor tolerancia) {
        if (tolerancia == null) {
            throw new RegraInvalida(
                    "A regra %s precisa da tolerância: sem ela, arredondamento de centavo viraria achado."
                            .formatted(ID));
        }
        this.tolerancia = tolerancia;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String versao() {
        return VERSAO;
    }

    @Override
    public Severidade severidade() {
        return Severidade.GRAVE;
    }

    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        List<Divergencia> divergencias = new ArrayList<>();
        List<String> pendencias = new ArrayList<>();

        for (Conferencia conferencia : conferencias(item)) {
            conferir(conferencia, contexto).aplicarEm(divergencias, pendencias);
        }

        if (!divergencias.isEmpty()) {
            return achadoDe(item, documento, divergencias);
        }
        if (!pendencias.isEmpty()) {
            return naoAvaliada(item, documento, String.join(" ", pendencias));
        }
        return conforme(item, documento);
    }

    /** Os três pares base e valor, sempre na mesma ordem. */
    private static List<Conferencia> conferencias(ItemDocumento item) {
        return List.of(
                new Conferencia(Tributo.IBS_UF, "baseCalculoIbs", item.baseCalculoIbs(),
                        "valorIbsUf", item.valorIbsUf()),
                new Conferencia(Tributo.IBS_MUN, "baseCalculoIbs", item.baseCalculoIbs(),
                        "valorIbsMunicipal", item.valorIbsMunicipal()),
                new Conferencia(Tributo.CBS, "baseCalculoCbs", item.baseCalculoCbs(),
                        "valorCbs", item.valorCbs()));
    }

    private Resultado conferir(Conferencia conferencia, ContextoNormativo contexto) {
        if (conferencia.base().isEmpty() && conferencia.valorInformado().isEmpty()) {
            return Resultado.pendente(
                    "O item não declarou %s nem %s."
                            .formatted(conferencia.nomeDaBase(), conferencia.nomeDoValor()));
        }
        if (conferencia.base().isEmpty()) {
            return Resultado.pendente(
                    "O item declarou %s mas não declarou %s; sem base não há produto a conferir."
                            .formatted(conferencia.nomeDoValor(), conferencia.nomeDaBase()));
        }
        if (conferencia.valorInformado().isEmpty()) {
            return Resultado.pendente(
                    "O item declarou %s mas não declarou %s; não há valor declarado a confrontar."
                            .formatted(conferencia.nomeDaBase(), conferencia.nomeDoValor()));
        }

        List<AliquotaVigente> aliquotas = contexto.aliquotas(conferencia.tributo()).stream()
                .sorted(Comparator.comparing(aliquota -> aliquota.abrangencia().valor()))
                .toList();
        if (aliquotas.isEmpty()) {
            return Resultado.pendente(
                    "O catálogo não traz alíquota vigente de %s na data de emissão."
                            .formatted(conferencia.tributo()));
        }
        if (aliquotas.size() > 1) {
            String abrangencias = aliquotas.stream().map(aliquota -> aliquota.abrangencia().valor())
                    .reduce((uma, outra) -> uma + ", " + outra).orElseThrow();
            return Resultado.pendente(
                    ("O catálogo traz %d alíquotas vigentes de %s na data (abrangências: %s) e a regra não "
                            + "tem como escolher entre elas.")
                            .formatted(aliquotas.size(), conferencia.tributo(), abrangencias));
        }

        AliquotaVigente aliquota = aliquotas.get(0);
        BigDecimal esperado = conferencia.base().orElseThrow()
                .multiply(aliquota.percentual())
                .movePointLeft(CASAS_DA_PORCENTAGEM);
        BigDecimal diferenca = conferencia.valorInformado().orElseThrow().subtract(esperado);

        if (tolerancia.acomoda(diferenca)) {
            return Resultado.conferido();
        }
        return Resultado.divergente(new Divergencia(conferencia, aliquota, esperado, diferenca));
    }

    private Avaliacao achadoDe(ItemDocumento item, Documento documento, List<Divergencia> divergencias) {
        List<Evidencia> evidencias = new ArrayList<>();
        BigDecimal somaDasDiferencas = BigDecimal.ZERO;

        for (Divergencia divergencia : divergencias) {
            Conferencia conferencia = divergencia.conferencia();
            evidencias.add(doDocumento(
                    conferencia.nomeDaBase(),
                    item,
                    conferencia.base().orElseThrow().toPlainString()));
            evidencias.add(daTabela(
                    conferencia.nomeDoValor(),
                    TABELA,
                    divergencia.aliquota().fonteNormativa(),
                    Optional.of(conferencia.valorInformado().orElseThrow().toPlainString()),
                    Optional.of(divergencia.esperado().toPlainString())));
            somaDasDiferencas = somaDasDiferencas.add(divergencia.diferenca().abs());
        }

        AliquotaVigente primeira = divergencias.get(0).aliquota();
        return comAchado(
                item,
                documento,
                List.copyOf(evidencias),
                primeira.fonteNormativa(),
                primeira.vigencia(),
                ValorEmRisco.calculado(somaDasDiferencas));
    }

    /** Um par base e valor a conferir para um tributo. */
    private record Conferencia(
            Tributo tributo,
            String nomeDaBase,
            Optional<BigDecimal> base,
            String nomeDoValor,
            Optional<BigDecimal> valorInformado) {
    }

    /** Um par que não fechou, com o que se esperava e de onde veio a referência. */
    private record Divergencia(
            Conferencia conferencia,
            AliquotaVigente aliquota,
            BigDecimal esperado,
            BigDecimal diferenca) {
    }

    /**
     * Desfecho de um par isolado.
     *
     * <p>Três estados, os mesmos do resultado final, porque o problema é o mesmo
     * em escala menor: conferiu, não fechou, ou não deu para conferir. Um par
     * pendente não pode desaparecer no meio do caminho e virar conformidade do
     * item.</p>
     */
    private record Resultado(Optional<Divergencia> divergencia, Optional<String> pendencia) {

        static Resultado conferido() {
            return new Resultado(Optional.empty(), Optional.empty());
        }

        static Resultado divergente(Divergencia divergencia) {
            return new Resultado(Optional.of(divergencia), Optional.empty());
        }

        static Resultado pendente(String motivo) {
            return new Resultado(Optional.empty(), Optional.of(motivo));
        }

        void aplicarEm(List<Divergencia> divergencias, List<String> pendencias) {
            divergencia.ifPresent(divergencias::add);
            pendencia.ifPresent(pendencias::add);
        }
    }
}
