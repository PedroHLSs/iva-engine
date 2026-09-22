package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MontadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A montagem das respostas, com dublês no lugar do banco.
 *
 * <p>O teste de contrato já prova a resposta por HTTP de verdade, mas depende de
 * Docker. Este cobre o que aquele não consegue cobrir barato: as contas do conforme
 * com números conferíveis à mão, e o lado <strong>ligado</strong> da política de
 * exposição — provar que o opt-in de fato opta, e não só que o padrão omite. Uma
 * chave que nunca aparece nem quando configurada seria um campo morto disfarçado de
 * decisão de privacidade.</p>
 *
 * <p>Roda sem contexto Spring, sem banco e sem Docker, que é o que mantém a maior
 * parte da suíte executável em qualquer máquina.</p>
 */
class MontadorDeRespostasTest {

    private static final UUID EXECUCAO = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ACHADO = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static final ChaveAcesso CHAVE = new ChaveAcesso("1".repeat(44));
    private static final String PSEUDONIMO = "a".repeat(64);
    private static final HashDoItem HASH_DO_ITEM = new HashDoItem("b".repeat(64));

    private static final String FUNDAMENTO = "Dispositivo ficticio para teste";
    private static final String MOTIVO_FICTICIO = "Motivo ficticio: falta tabela no catalogo de teste.";
    private static final String JUSTIFICATIVA = "Justificativa ficticia de teste.";

    private static final int ITENS = 10;
    private static final Instant QUANDO = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void deveDerivarOConformeDaRodadaMostrandoAConta() {
        RespostaDaExecucao resposta = montar(PoliticaDeExposicao.restritiva()).execucao(EXECUCAO);

        assertThat(resposta.desfechos().avaliacoesProduzidas())
                .as("10 itens x 2 regras: o motor produz uma avaliação por par (item, regra)")
                .isEqualTo(20);
        assertThat(resposta.desfechos().achado()).isEqualTo(1);
        assertThat(resposta.desfechos().naoAvaliado()).isEqualTo(3);
        assertThat(resposta.desfechos().conforme().valor())
                .as("20 - 1 - 3")
                .isEqualTo(16L);
        assertThat(resposta.desfechos().conforme().derivacao())
                .as("a conta vai escrita, porque este número não está gravado em coluna nenhuma")
                .isEqualTo("avaliacoesProduzidas 20 - achado 1 - naoAvaliado 3");
        assertThat(resposta.desfechos().conforme().motivoDaAusencia()).isNull();
    }

    @Test
    void deveDerivarOConformeDeCadaRegraSobreOsItensDaRodada() {
        RespostaDaExecucao resposta = montar(PoliticaDeExposicao.restritiva()).execucao(EXECUCAO);

        RespostaDaExecucao.PorRegra primeira = resposta.porRegra().get(0);
        RespostaDaExecucao.PorRegra segunda = resposta.porRegra().get(1);

        assertThat(primeira.regraId()).isEqualTo("R01");
        assertThat(primeira.achado()).isEqualTo(1);
        assertThat(primeira.naoAvaliado()).isZero();
        assertThat(primeira.conforme().valor()).as("10 itens - 1 achado").isEqualTo(9L);

        assertThat(segunda.regraId()).isEqualTo("R02");
        assertThat(segunda.achado())
                .as("regra que rodou e nada apontou continua no relatório, com zero escrito")
                .isZero();
        assertThat(segunda.naoAvaliado()).isEqualTo(3);
        assertThat(segunda.conforme().valor()).as("10 itens - 3 não avaliadas").isEqualTo(7L);
        assertThat(segunda.motivosDoNaoAvaliado())
                .singleElement()
                .satisfies(motivo -> {
                    assertThat(motivo.motivo()).isEqualTo(MOTIVO_FICTICIO);
                    assertThat(motivo.quantidade()).isEqualTo(3);
                });
    }

    // Acrescentado depois da Etapa 11: o nome da regra vai ao lado do código.
    @Test
    void deveEscreverONomeDaRegraAoLadoDoCodigoEmTodaResposta() {
        MontadorDeRespostas montador = montar(PoliticaDeExposicao.restritiva());

        assertThat(montador.execucao(EXECUCAO).porRegra())
                .allSatisfy(regra -> {
                    assertThat(regra.regraNome()).isEqualTo(NomeDaRegra.de(regra.regraId()).nome());
                    assertThat(regra.motivoDoNomeDaRegraAusente()).isNull();
                });
        assertThat(montador
                .achados(EXECUCAO, Optional.empty(), Optional.empty(), Optional.empty(), 0, 50)
                .achados())
                .allSatisfy(achado -> assertThat(achado.regraNome()).isNotBlank());
        assertThat(montador.naoAvaliados(EXECUCAO, Optional.empty(), 0, 50).naoAvaliados())
                .isNotEmpty()
                .allSatisfy(naoAvaliada -> {
                    assertThat(naoAvaliada.regraId())
                            .as("o código continua: é ele que a CLI e a planilha usam")
                            .isEqualTo("R02");
                    assertThat(naoAvaliada.regraNome()).isNotBlank();
                });
    }

    @Test
    void deveOmitirChaveEJustificativaComOMotivoQuandoAExposicaoEstaDesligada() {
        RespostaDeAchados resposta = montar(PoliticaDeExposicao.restritiva())
                .achados(EXECUCAO, Optional.empty(), Optional.empty(), Optional.empty(), 0, 50);

        AchadoExposto achado = resposta.achados().get(0);

        assertThat(achado.documento().chaveAcesso()).isNull();
        assertThat(achado.documento().motivoDaChaveOmitida()).contains("CNPJ do emitente");
        assertThat(achado.documento().pseudonimo())
                .as("o pseudônimo continua identificando o documento entre respostas")
                .isEqualTo(PSEUDONIMO);

        assertThat(achado.tratativa().justificativa()).isNull();
        assertThat(achado.tratativa().motivoDaJustificativaOmitida())
                .contains("CNPJ ou razão social");
        assertThat(achado.tratativa().decisao())
                .as("a decisão em si não é dado pessoal e continua aparecendo")
                .isEqualTo(DecisaoDeTratativa.ACEITO.name());
    }

    @Test
    void deveExporChaveEJustificativaQuandoAInstalacaoAsLiga() {
        RespostaDeAchados resposta = montar(new PoliticaDeExposicao(true, true, true))
                .achados(EXECUCAO, Optional.empty(), Optional.empty(), Optional.empty(), 0, 50);

        AchadoExposto achado = resposta.achados().get(0);

        assertThat(achado.documento().chaveAcesso()).isEqualTo(CHAVE.valor());
        assertThat(achado.documento().motivoDaChaveOmitida())
                .as("ligada a exposição, o motivo desaparece: os dois campos são complementares, e "
                        + "nunca preenchidos ao mesmo tempo")
                .isNull();

        assertThat(achado.tratativa().justificativa()).isEqualTo(JUSTIFICATIVA);
        assertThat(achado.tratativa().motivoDaJustificativaOmitida()).isNull();
    }

    @Test
    void deveEscreverOResultadoPorExtensoEmAchadoEEmNaoAvaliado() {
        MontadorDeRespostas montador = montar(PoliticaDeExposicao.restritiva());

        assertThat(montador
                .achados(EXECUCAO, Optional.empty(), Optional.empty(), Optional.empty(), 0, 50)
                .achados())
                .allSatisfy(achado -> assertThat(achado.resultado())
                        .isEqualTo(ResultadoAvaliacao.ACHADO.name()));

        assertThat(montador.naoAvaliados(EXECUCAO, Optional.empty(), 0, 50).naoAvaliados())
                .isNotEmpty()
                .allSatisfy(naoAvaliada -> assertThat(naoAvaliada.resultado())
                        .as("sem isto a distinção viveria só no nome do campo que contém a lista")
                        .isEqualTo(ResultadoAvaliacao.NAO_AVALIADO.name()));
    }

    @Test
    void devePaginarSemPerderALinhaNemRepetiLa() {
        MontadorDeRespostas montador = montar(PoliticaDeExposicao.restritiva());

        RespostaDeNaoAvaliados primeira = montador.naoAvaliados(EXECUCAO, Optional.empty(), 0, 2);
        RespostaDeNaoAvaliados segunda = montador.naoAvaliados(EXECUCAO, Optional.empty(), 1, 2);
        RespostaDeNaoAvaliados terceira = montador.naoAvaliados(EXECUCAO, Optional.empty(), 2, 2);

        assertThat(primeira.naoAvaliados()).hasSize(2);
        assertThat(segunda.naoAvaliados()).hasSize(1);
        assertThat(terceira.naoAvaliados())
                .as("página além do fim é vazia, não é erro")
                .isEmpty();
        assertThat(primeira.pagina().totalDeElementos())
                .as("o total é o de antes do recorte, sempre")
                .isEqualTo(3);
        assertThat(primeira.pagina().totalDePaginas()).isEqualTo(2);
    }

    @Test
    void deveFiltrarPorRegraDevolvendoOFiltroAplicado() {
        RespostaDeNaoAvaliados resposta = montar(PoliticaDeExposicao.restritiva())
                .naoAvaliados(EXECUCAO, Optional.of("R99"), 0, 50);

        assertThat(resposta.naoAvaliados()).isEmpty();
        assertThat(resposta.filtro().regraId())
                .as("o filtro volta escrito: lista vazia sem ele é indistinguível de acervo limpo")
                .isEqualTo("R99");
        assertThat(resposta.pagina().totalDeElementos()).isZero();
    }

    private MontadorDeRespostas montar(PoliticaDeExposicao politica) {
        ConsultaDeExecucoes execucoes = new ExecucoesFalsas();
        ConsultaDeAchadosDaExecucao achados = new AchadosFalsos();
        ConsultaDeNaoAvaliadas naoAvaliadas = new NaoAvaliadasFalsas();
        ConsultaDeDocumentos documentos = new DocumentosFalsos();
        PseudonimizadorDeChave pseudonimizador = chave -> new IdentificadorPseudonimizado(PSEUDONIMO);

        return new MontadorDeRespostas(
                execucoes,
                achados,
                naoAvaliadas,
                documentos,
                pseudonimizador,
                new MontadorDePapelDeTrabalho(achados, naoAvaliadas, documentos, pseudonimizador),
                politica);
    }

    /**
     * Execução com 10 itens, duas regras aplicadas e um apontamento.
     *
     * <p>As contagens são escolhidas para a conta do conforme dar um número que se
     * confere de cabeça, e para a segunda regra ter apontado zero — que é o caso que
     * some do relatório quando alguém "otimiza" a resposta.</p>
     */
    private static ExecucaoAuditoria execucaoFicticia() {
        Map<Severidade, Integer> porSeveridade = new LinkedHashMap<>();
        porSeveridade.put(Severidade.CRITICA, 0);
        porSeveridade.put(Severidade.GRAVE, 1);
        porSeveridade.put(Severidade.MODERADA, 0);
        porSeveridade.put(Severidade.INFORMATIVA, 0);

        Map<String, Integer> porRegra = new LinkedHashMap<>();
        porRegra.put("R01", 1);
        porRegra.put("R02", 0);

        return new ExecucaoAuditoria(
                EXECUCAO,
                QUANDO,
                "c".repeat(64),
                "carga-ficticia",
                "2026.1",
                1,
                ITENS,
                porSeveridade,
                porRegra);
    }

    private static AchadoRegistrado achadoFicticio() {
        Achado achado = new Achado(
                "R01",
                "1.0.0",
                Severidade.GRAVE,
                CHAVE,
                OptionalInt.of(1),
                List.of(new Evidencia(
                        "cstIbs",
                        Optional.of("AAA"),
                        Optional.of("999"),
                        new OrigemEvidencia.DaRegra("Regra ficticia de teste"))),
                FUNDAMENTO,
                PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1)),
                ValorEmRisco.calculado(new BigDecimal("99.99")));

        return new AchadoRegistrado(
                ACHADO,
                achado,
                HASH_DO_ITEM,
                Optional.of(new Tratativa(
                        new ChaveDeTratativa(HASH_DO_ITEM, "R01", "1.0.0"),
                        DecisaoDeTratativa.ACEITO,
                        JUSTIFICATIVA,
                        QUANDO)),
                QUANDO,
                QUANDO);
    }

    private static final class ExecucoesFalsas implements ConsultaDeExecucoes {

        @Override
        public Optional<ExecucaoAuditoria> maisRecente() {
            return Optional.of(execucaoFicticia());
        }

        @Override
        public Optional<ExecucaoAuditoria> porId(UUID id) {
            return EXECUCAO.equals(id) ? Optional.of(execucaoFicticia()) : Optional.empty();
        }

        @Override
        public List<ExecucaoAuditoria> ultimas(int quantidade) {
            return List.of(execucaoFicticia());
        }
    }

    private static final class AchadosFalsos implements ConsultaDeAchadosDaExecucao {

        @Override
        public List<AchadoRegistrado> daExecucao(UUID execucaoId) {
            return EXECUCAO.equals(execucaoId) ? List.of(achadoFicticio()) : List.of();
        }
    }

    /** Três não concluídas da mesma regra e com o mesmo motivo: um grupo de três. */
    private static final class NaoAvaliadasFalsas implements ConsultaDeNaoAvaliadas {

        @Override
        public List<NaoAvaliadaRegistrada> daExecucao(UUID execucaoId) {
            if (!EXECUCAO.equals(execucaoId)) {
                return List.of();
            }
            return List.of(
                    new NaoAvaliadaRegistrada(CHAVE, 1, "R02", "1.0.0", MOTIVO_FICTICIO),
                    new NaoAvaliadaRegistrada(CHAVE, 2, "R02", "1.0.0", MOTIVO_FICTICIO),
                    new NaoAvaliadaRegistrada(CHAVE, 3, "R02", "1.0.0", MOTIVO_FICTICIO));
        }
    }

    private static final class DocumentosFalsos implements ConsultaDeDocumentos {

        @Override
        public Map<ChaveAcesso, DadosDoDocumento> porChaves(Collection<ChaveAcesso> chaves) {
            return Map.of(CHAVE, new DadosDoDocumento(
                    CHAVE, "55", "1", "999", LocalDate.of(2026, 1, 15), Uf.MG));
        }
    }
}
