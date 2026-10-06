package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.IncidenciaDaReducao;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Regra R05: o valor do tributo na nota bate com base × alíquota do catálogo, dentro da tolerância? Gravidade: grave. Só faz a conta quando o catálogo tem uma única alíquota para o tributo na data. Desde a 1.1.0 (30/09/2026) aplica a redução de alíquota que o catálogo declara para o cClassTrib: esperado = base × alíquota × (1 − redução/100).
// Versão 1.2.0 (03/10/2026, D016): recebe a cobertura declarada da tabela de classificações. Quando o código não está na tabela e a tabela não cobre a data da nota, NAO_AVALIADO — na 1.1.0 a regra assumia redução zero e recalculava com alíquota cheia, e acusava ou aprovava nota sem saber o tratamento dela. Dentro da cobertura, código ausente continua sem redução (D4).
// Versão 1.3.0 (03/10/2026, D017): a redução de base deixou de ser deduzida dos CST 210 e 222, escritos neste arquivo, e passou a ser lida no catálogo (coluna reducaoIncideSobre). O CST do item deixou de ser consultado aqui; compatibilidade de CST é da R02.
public final class RegraValorDeTributoConfere extends RegraDeItem {

    public static final String ID = "R05";
    public static final String VERSAO = "1.3.0";

    static final String TABELA = "catalogo:aliquota";

    // O percentual do catálogo é lido como porcentagem, ou seja, dividido por 100.
    private static final int CASAS_DA_PORCENTAGEM = 2;

    private static final BigDecimal CEM = new BigDecimal("100");

    private final ToleranciaDeValor tolerancia;
    private final ProcedenciaNormativa coberturaDasClassificacoes;

    // Construtor que recebe a tolerância e a cobertura declarada da tabela de classificações. Sem tolerância, diferença de centavo por arredondamento viraria achado; sem cobertura, silêncio da tabela viraria "redução zero".
    public RegraValorDeTributoConfere(
            ToleranciaDeValor tolerancia, ProcedenciaNormativa coberturaDasClassificacoes) {
        if (tolerancia == null) {
            throw new RegraInvalida(
                    "A regra %s precisa da tolerância: sem ela, arredondamento de centavo viraria achado."
                            .formatted(ID));
        }
        this.tolerancia = tolerancia;
        this.coberturaDasClassificacoes = exigirCobertura(coberturaDasClassificacoes, "classificações tributárias");
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

    // Aplica a regra nos três valores (IBS estadual, IBS municipal e CBS). Se algum não bate, gera achado; se nenhum diverge mas algum não pôde ser conferido, NAO_AVALIADO; só é CONFORME se os três foram conferidos e bateram. Desde a 1.1.0, sem fator de redução definido a regra não faz conta nenhuma.
    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Fator fator = fatorDeReducao(item, documento, contexto);
        if (fator.pendencia().isPresent()) {
            return naoAvaliada(item, documento, fator.pendencia().get());
        }

        List<Divergencia> divergencias = new ArrayList<>();
        List<String> pendencias = new ArrayList<>();

        for (Conferencia conferencia : conferencias(item)) {
            conferir(conferencia, fator.reducao(), contexto).aplicarEm(divergencias, pendencias);
        }

        if (!divergencias.isEmpty()) {
            return achadoDe(item, documento, divergencias);
        }
        if (!pendencias.isEmpty()) {
            return naoAvaliada(item, documento, String.join(" ", pendencias));
        }
        return conforme(item, documento);
    }

    // Método auxiliar que decide a redução a aplicar: sem cClassTrib, nenhuma (D4); código fora do catálogo, nenhuma se a tabela cobre a data (D4) e pendência se não cobre (D016); redução em branco ou CST de redução de base com redução diferente de zero, pendência (decisões D4 e D5 de 30/09/2026).
    private Fator fatorDeReducao(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            // D4: o item não declarou cClassTrib, a tabela não é consultada, e a conta é a da alíquota cheia.
            return Fator.semReducao();
        }
        Optional<ClassificacaoTributaria> registro = contexto.classificacaoTributaria(codigo.get());
        if (registro.isEmpty()) {
            if (!coberturaDasClassificacoes.vigenteEm(documento.dataEmissao())) {
                // D016: fora da cobertura, a tabela não dizer nada sobre o código é falta de dado (D004), e não "redução zero".
                return Fator.pendente(
                        ("O catálogo nada diz sobre o cClassTrib \"%s\", e a tabela de classificações carregada "
                                + "cobre a partir de %s%s, sem alcançar a data de emissão %s. Fora da cobertura não "
                                + "há como saber se o código tem redução, e a regra não assume que não tem.")
                                .formatted(codigo.get().valor(),
                                        coberturaDasClassificacoes.vigenciaInicio(),
                                        coberturaDasClassificacoes.vigenciaFim().map(" até %s"::formatted).orElse(""),
                                        documento.dataEmissao()));
            }
            // D4: dentro da cobertura, a tabela foi carregada para a data e não traz o código; a conta é a da alíquota cheia, como na 1.0.0.
            return Fator.semReducao();
        }
        ClassificacaoTributaria classificacao = registro.get();
        if (classificacao.percentualReducao().isEmpty()) {
            return Fator.pendente(
                    ("O catálogo não declara a redução do cClassTrib \"%s\" na data de emissão; redução em "
                            + "branco não é zero, e sem ela não há valor esperado a calcular.")
                            .formatted(classificacao.codigo().valor()));
        }
        BigDecimal reducao = classificacao.percentualReducao().get();
        if (reducao.signum() == 0) {
            return Fator.semReducao();
        }
        // D017: quem diz se a redução incide sobre a base é o catálogo, na coluna reducaoIncideSobre. Não declarado é alíquota, como a decisão D1 definiu para a coluna de redução.
        if (classificacao.reducaoIncideSobre().filter(IncidenciaDaReducao.BASE::equals).isPresent()) {
            return Fator.pendente(
                    ("O cClassTrib \"%s\" declara redução de %s no catálogo, e o catálogo declara que ela incide "
                            + "sobre a base de cálculo (reducaoIncideSobre = BASE): redução de base não suportada, "
                            + "porque a regra aplica a redução sobre a alíquota.")
                            .formatted(classificacao.codigo().valor(), reducao.toPlainString()));
        }
        return Fator.com(reducao);
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

    // Método auxiliar que confere um par: precisa da base, do valor e de uma única alíquota; calcula base × alíquota ÷ 100, aplica a redução quando houver, e compara com o valor da nota.
    private Resultado conferir(Conferencia conferencia, Optional<BigDecimal> reducao, ContextoNormativo contexto) {
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
        BigDecimal cheio = conferencia.base().orElseThrow()
                .multiply(aliquota.percentual())
                .movePointLeft(CASAS_DA_PORCENTAGEM);
        // Sem redução a conta é exatamente a da 1.0.0, inclusive na escala do valor esperado.
        BigDecimal esperado = reducao
                .map(percentual -> cheio.multiply(CEM.subtract(percentual)).movePointLeft(CASAS_DA_PORCENTAGEM))
                .orElse(cheio);
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

    // Guarda a redução a aplicar no item, ou o motivo de não haver como definir uma.
    private record Fator(Optional<BigDecimal> reducao, Optional<String> pendencia) {

        // Cria o fator sem redução: a conta é base × alíquota.
        static Fator semReducao() {
            return new Fator(Optional.empty(), Optional.empty());
        }

        // Cria o fator com a redução declarada pelo catálogo.
        static Fator com(BigDecimal reducao) {
            return new Fator(Optional.of(reducao), Optional.empty());
        }

        // Cria o fator que não pôde ser definido, com o motivo.
        static Fator pendente(String motivo) {
            return new Fator(Optional.empty(), Optional.of(motivo));
        }
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
