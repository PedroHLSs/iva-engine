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

// Regra R05: o valor do tributo na nota bate com base × alíquota do catálogo, dentro da tolerância? Gravidade: grave. Só faz a conta quando o catálogo tem uma única alíquota para o tributo na data.
public final class RegraValorDeTributoConfere extends RegraDeItem {

    public static final String ID = "R05";
    public static final String VERSAO = "1.0.0";

    static final String TABELA = "catalogo:aliquota";

    // O percentual do catálogo é lido como porcentagem, ou seja, dividido por 100.
    private static final int CASAS_DA_PORCENTAGEM = 2;

    private final ToleranciaDeValor tolerancia;

    // Construtor que recebe a tolerância; sem ela, diferença de centavo por arredondamento viraria achado.
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

    // Aplica a regra nos três valores (IBS estadual, IBS municipal e CBS). Se algum não bate, gera achado; se nenhum diverge mas algum não pôde ser conferido, NAO_AVALIADO; só é CONFORME se os três foram conferidos e bateram.
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

    // Método auxiliar que monta os três pares de base e valor, sempre na ordem IBS estadual, IBS municipal e CBS.
    private static List<Conferencia> conferencias(ItemDocumento item) {
        return List.of(
                new Conferencia(Tributo.IBS_UF, "baseCalculoIbs", item.baseCalculoIbs(),
                        "valorIbsUf", item.valorIbsUf()),
                new Conferencia(Tributo.IBS_MUN, "baseCalculoIbs", item.baseCalculoIbs(),
                        "valorIbsMunicipal", item.valorIbsMunicipal()),
                new Conferencia(Tributo.CBS, "baseCalculoCbs", item.baseCalculoCbs(),
                        "valorCbs", item.valorCbs()));
    }

    // Método auxiliar que confere um par: precisa da base, do valor e de uma única alíquota; calcula base × alíquota ÷ 100 e compara com o valor da nota.
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

    // Método auxiliar que monta o achado com as evidências de cada valor que não bateu e soma as diferenças como valor em risco.
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

    // Guarda um par de base e valor de um tributo para conferir.
    private record Conferencia(
            Tributo tributo,
            String nomeDaBase,
            Optional<BigDecimal> base,
            String nomeDoValor,
            Optional<BigDecimal> valorInformado) {
    }

    // Guarda um par que não bateu, com o valor esperado e a alíquota usada na conta.
    private record Divergencia(
            Conferencia conferencia,
            AliquotaVigente aliquota,
            BigDecimal esperado,
            BigDecimal diferenca) {
    }

    // Resultado da conferência de um par: bateu, não bateu ou não deu para conferir. Assim um par que não foi conferido não passa como se estivesse certo.
    private record Resultado(Optional<Divergencia> divergencia, Optional<String> pendencia) {

        // Cria o resultado de par que bateu dentro da tolerância.
        static Resultado conferido() {
            return new Resultado(Optional.empty(), Optional.empty());
        }

        // Cria o resultado de par que não bateu.
        static Resultado divergente(Divergencia divergencia) {
            return new Resultado(Optional.of(divergencia), Optional.empty());
        }

        // Cria o resultado de par que não deu para conferir, com o motivo.
        static Resultado pendente(String motivo) {
            return new Resultado(Optional.empty(), Optional.of(motivo));
        }

        // Coloca o resultado do par na lista de divergências ou na de pendências.
        void aplicarEm(List<Divergencia> divergencias, List<String> pendencias) {
            divergencia.ifPresent(divergencias::add);
            pendencia.ifPresent(pendencias::add);
        }
    }
}
