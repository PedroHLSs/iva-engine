package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.acuracia.MetricasDaRegra;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
import br.edu.tcc.auditoria.dominio.acuracia.Metrica;
import br.edu.tcc.auditoria.infraestrutura.acuracia.TextoDeMetrica;

import java.util.List;

// Representa uma medição de acurácia feita pela web. Toda linha de métrica carrega a versão do catálogo, a do conjunto de regras e a cobertura, e o construtor exige as três: duas medições com catálogos diferentes dão números diferentes, e quem lê precisa ver isso sem investigar. Métrica sem item avaliado sai como "(indefinida)", nunca zero. Acrescentada na Etapa 13.
public record RespostaDaAcuracia(
        String versaoDoCatalogo,
        String versaoDoConjuntoDeRegras,
        int documentosAuditados,
        int itensAuditados,
        int avaliacoesSemLinhaNoGabarito,
        int linhasDoGabaritoSemAvaliacao,
        List<MetricasExpostas> porRegra,
        MetricasExpostas consolidado,
        String comoOConsolidadoEObtido,
        String porQueNaoHaMediaMacro,
        String comoONaoAvaliadoEntra,
        FaixaDeNatureza natureza,
        ToleranciaExposta toleranciaDeValor) {

    // Emenda de 04/10/2026 (D023): a medição traz a tolerância de valor da R05 com que foi feita; as métricas da R05 dependem dela.

    // Emenda de 04/10/2026 (D021): a medição traz a faixa de procedência do catálogo contra o qual foi feita. Métrica medida contra catálogo fictício mede o motor sobre dado de demonstração.

    static final String CONSOLIDADO_MICRO =
            "Consolidado micro: soma das células de todas as regras, e só depois as contas. Não é média das "
                    + "regras: uma regra com muitas linhas rotuladas pesa mais aqui, e por isso a tabela por "
                    + "regra está sempre ao lado.";

    static final String SEM_MACRO =
            "Não há média macro por regra. Ela obrigaria a decidir o que fazer com as regras de métrica "
                    + "indefinida, e essa decisão não tem resposta defensável (D008; pedida e removida na D014).";

    static final String NAO_AVALIADO =
            "Item não avaliado fica fora de precisão, recall e F1, e aparece só na cobertura: avaliados sobre "
                    + "total. Somá-lo a acerto ou a erro inventaria um desfecho que o motor não deu.";

    // Valida que haja versões e as linhas de métrica.
    public RespostaDaAcuracia {
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()
                || versaoDoConjuntoDeRegras == null || versaoDoConjuntoDeRegras.isBlank()) {
            throw new RespostaInvalida(
                    "A medição precisa dizer contra que catálogo e que conjunto de regras foi feita.");
        }
        if (porRegra == null || consolidado == null) {
            throw new RespostaInvalida("A medição precisa das linhas por regra e do consolidado.");
        }
        if (natureza == null) {
            throw new RespostaInvalida("A medição traz a faixa de procedência do catálogo (D021).");
        }
        if (toleranciaDeValor == null) {
            throw new RespostaInvalida(
                    "A resposta traz a tolerância de valor da R05 que a execução usou, ou o motivo de não haver "
                            + "(D023): duas execuções com tolerâncias diferentes dão resultados diferentes.");
        }
        porRegra = List.copyOf(porRegra);
    }

    // Representa as métricas de uma regra, ou do consolidado, com as versões e a cobertura na mesma linha.
    public record MetricasExpostas(
            String rotulo,
            String regraId,
            String versaoDoCatalogo,
            String versaoDoConjuntoDeRegras,
            int verdadeirosPositivos,
            int falsosPositivos,
            int falsosNegativos,
            int verdadeirosNegativos,
            int naoAvaliados,
            int semAvaliacao,
            int avaliados,
            int total,
            MetricaExposta precisao,
            MetricaExposta recall,
            MetricaExposta f1,
            MetricaExposta cobertura) {

        // Valida que a linha traga as versões e as quatro métricas, cobertura inclusive.
        public MetricasExpostas {
            if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()
                    || versaoDoConjuntoDeRegras == null || versaoDoConjuntoDeRegras.isBlank()) {
                throw new RespostaInvalida(
                        "Métrica sem a versão do catálogo e do conjunto de regras não pode ser exibida.");
            }
            if (precisao == null || recall == null || f1 == null || cobertura == null) {
                throw new RespostaInvalida("A linha de métrica precisa de precisão, recall, F1 e cobertura.");
            }
        }
    }

    // Representa uma métrica: o texto como deve aparecer, o valor quando definida, ou o motivo quando indefinida.
    public record MetricaExposta(String texto, String valor, String motivoDaIndefinicao) {

        // Valida que venha o valor ou o motivo, nunca os dois, e que indefinida seja escrita como tal.
        public MetricaExposta {
            if (texto == null || texto.isBlank()) {
                throw new RespostaInvalida("A métrica precisa do texto a exibir; célula vazia não é resposta.");
            }
            if ((valor == null) == (motivoDaIndefinicao == null)) {
                throw new RespostaInvalida("A métrica traz o valor ou o motivo de ser indefinida, nunca os dois.");
            }
            if (valor == null && !TextoDeMetrica.INDEFINIDA.equals(texto)) {
                throw new RespostaInvalida("Métrica indefinida sai escrita como " + TextoDeMetrica.INDEFINIDA + ".");
            }
        }

        // Método estático que converte a métrica do domínio.
        static MetricaExposta de(Metrica metrica) {
            return new MetricaExposta(
                    TextoDeMetrica.de(metrica),
                    metrica.valor().map(valor -> valor.toPlainString()).orElse(null),
                    metrica.motivoDaIndefinicao().orElse(null));
        }
    }

    // Método estático que monta a resposta a partir do relatório do harness, sem recalcular nada.
    static RespostaDaAcuracia de(RelatorioDeAcuracia relatorio, NaturezaDaCarga natureza) {
        String catalogo = relatorio.versaoDoCatalogo();
        String regras = relatorio.versaoDoConjuntoDeRegras();
        List<MetricasExpostas> porRegra = relatorio.porRegra().stream()
                .map(metricas -> linha(nomeDe(metricas), metricas.regraId(), metricas.contagem(), catalogo, regras))
                .toList();
        return new RespostaDaAcuracia(
                catalogo,
                regras,
                relatorio.documentosAuditados(),
                relatorio.itensAuditados(),
                relatorio.avaliacoesSemLinhaNoGabarito(),
                relatorio.gabaritoSemAvaliacao().size(),
                porRegra,
                linha("Consolidado (micro)", null, relatorio.consolidado(), catalogo, regras),
                CONSOLIDADO_MICRO,
                SEM_MACRO,
                NAO_AVALIADO,
                FaixaDeNatureza.de(natureza, catalogo),
                ToleranciaExposta.de(relatorio.tolerancia()));
    }

    // Método auxiliar que devolve o nome da regra por extenso, ou o código quando a tabela não o conhece.
    private static String nomeDe(MetricasDaRegra metricas) {
        NomeDaRegra nome = NomeDaRegra.de(metricas.regraId());
        return nome.nome() != null ? nome.nome() : metricas.regraId();
    }

    // Método auxiliar que monta uma linha de métrica.
    private static MetricasExpostas linha(
            String rotulo, String regraId, ContagemDeAcuracia contagem, String catalogo, String regras) {
        return new MetricasExpostas(
                rotulo, regraId, catalogo, regras,
                contagem.verdadeirosPositivos(), contagem.falsosPositivos(),
                contagem.falsosNegativos(), contagem.verdadeirosNegativos(),
                contagem.naoAvaliados(), contagem.semAvaliacao(),
                contagem.avaliados(), contagem.total(),
                MetricaExposta.de(contagem.precisao()),
                MetricaExposta.de(contagem.recall()),
                MetricaExposta.de(contagem.f1()),
                MetricaExposta.de(contagem.cobertura()));
    }
}
