package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.LoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;
import br.edu.tcc.auditoria.dominio.regras.RegraClassificacaoTributariaExiste;
import br.edu.tcc.auditoria.dominio.regras.RegraCstCompativelComClassificacao;
import br.edu.tcc.auditoria.dominio.regras.RegraNcmExiste;
import br.edu.tcc.auditoria.dominio.regras.RegraValorDeTributoConfere;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioAliquotaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioClassificacaoTributariaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioItemAnexoEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioNcmEmMemoria;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O harness de ponta a ponta, com o motor e o catálogo de verdade e dublês
 * apenas para a leitura dos arquivos.
 *
 * <h2>O cenário e o que ele produz</h2>
 *
 * <p>Um documento fictício com um item que declara CST incompatível com a
 * classificação, sobre um catálogo que reconhece o código e o NCM mas não traz
 * alíquota nenhuma. Contra esse item, as sete regras concluem:</p>
 *
 * <ul>
 *   <li>{@code R02} aponta — o CST não está entre os que a classificação
 *       admite;</li>
 *   <li>{@code R05} <strong>não avalia</strong> — sem alíquota no catálogo não há
 *       como conferir valor de tributo;</li>
 *   <li>as demais concluem conformes.</li>
 * </ul>
 *
 * <p>O gabarito rotula quatro dessas avaliações, escolhidas para dar um
 * verdadeiro positivo, um verdadeiro negativo, um falso negativo e um não
 * avaliado. Consolidando: VP=1, FP=0, FN=1, VN=1, NAv=1 — precisão 1/1 = 1,
 * recall 1/2 = 0,5, F1 = 2/(2+0+1) = 0,6667 e cobertura 3/4 = 0,75.</p>
 *
 * <p><strong>Todos os valores são fictícios.</strong></p>
 */
class ServicoDeAvaliacaoDeAcuraciaTest {

    private static final Path ORIGEM = Path.of("origem-ficticia");
    private static final Path GABARITO = Path.of("gabarito-ficticio.csv");
    private static final Path RELATORIO = Path.of("relatorio-ficticio.csv");

    private static final String CHAVE = CenarioFicticio.CHAVE_PRIMEIRA;

    private final EscritorFalso escritor = new EscritorFalso();

    @Test
    void deveMedirOMotorContraOGabaritoConsolidandoAsCelulas() {
        RelatorioDeAcuracia relatorio = servicoCom(gabaritoDeQuatroLinhas()).avaliar(ORIGEM, GABARITO);

        assertThat(relatorio.consolidado()).isEqualTo(new ContagemDeAcuracia(1, 0, 1, 1, 1, 0));
        assertThat(relatorio.consolidado().precisao().valor()).contains(new BigDecimal("1.0000"));
        assertThat(relatorio.consolidado().recall().valor()).contains(new BigDecimal("0.5000"));
        assertThat(relatorio.consolidado().f1().valor()).contains(new BigDecimal("0.6667"));
        assertThat(relatorio.consolidado().cobertura().valor()).contains(new BigDecimal("0.7500"));
    }

    @Test
    void deveDarVerdadeiroPositivoNaRegraQueApontouOQueOGabaritoAfirma() {
        ContagemDeAcuracia r02 = contagemDe(RegraCstCompativelComClassificacao.ID);

        assertThat(r02).isEqualTo(new ContagemDeAcuracia(1, 0, 0, 0, 0, 0));
        assertThat(r02.precisao().valor()).contains(new BigDecimal("1.0000"));
        assertThat(r02.recall().valor()).contains(new BigDecimal("1.0000"));
        assertThat(r02.f1().valor()).contains(new BigDecimal("1.0000"));
    }

    @Test
    void regraQueNaoConcluiuDeveContarComoNaoAvaliadaEFicarComMetricasIndefinidas() {
        ContagemDeAcuracia r05 = contagemDe(RegraValorDeTributoConfere.ID);

        assertThat(r05.naoAvaliados())
                .as("sem alíquota no catálogo a regra de valor não conclui, e isso não é conformidade")
                .isEqualTo(1);
        assertThat(r05.avaliados()).isZero();
        assertThat(r05.precisao().estaDefinida()).isFalse();
        assertThat(r05.recall().estaDefinida()).isFalse();
        assertThat(r05.f1().estaDefinida()).isFalse();
        assertThat(r05.cobertura().valor())
                .as("a regra não julgou nenhuma das linhas que o gabarito lhe deu")
                .contains(new BigDecimal("0.0000"));
    }

    @Test
    void naoAvaliadoNaoPodeMudarPrecisaoNemRecallConsolidados() {
        RelatorioDeAcuracia semNaoAvaliado = servicoCom(gabaritoSemALinhaNaoAvaliada())
                .avaliar(ORIGEM, GABARITO);
        RelatorioDeAcuracia comNaoAvaliado = servicoCom(gabaritoDeQuatroLinhas())
                .avaliar(ORIGEM, GABARITO);

        assertThat(comNaoAvaliado.consolidado().precisao())
                .isEqualTo(semNaoAvaliado.consolidado().precisao());
        assertThat(comNaoAvaliado.consolidado().recall())
                .isEqualTo(semNaoAvaliado.consolidado().recall());
        assertThat(comNaoAvaliado.consolidado().f1())
                .isEqualTo(semNaoAvaliado.consolidado().f1());

        assertThat(semNaoAvaliado.consolidado().cobertura().valor())
                .as("sem a linha não avaliada, tudo o que o gabarito pediu foi julgado")
                .contains(new BigDecimal("1.0000"));
        assertThat(comNaoAvaliado.consolidado().cobertura().valor())
                .as("a linha não avaliada aparece aqui, e só aqui")
                .contains(new BigDecimal("0.7500"));
    }

    @Test
    void deveDarLinhaZeradaAsRegrasQueOGabaritoNaoRotula() {
        RelatorioDeAcuracia relatorio = servicoCom(gabaritoDeQuatroLinhas()).avaliar(ORIGEM, GABARITO);

        assertThat(relatorio.porRegra())
                .extracting(MetricasDaRegra::regraId)
                .containsExactlyElementsOf(
                        ConjuntoRegras.padrao(
                                        CenarioFicticio.coberturaTotal(),
                                        ToleranciaDeValor.de(new BigDecimal("0.01")))
                                .identificadores());
        assertThat(relatorio.daRegra("R03").orElseThrow().contagem())
                .isEqualTo(ContagemDeAcuracia.nenhuma());
    }

    @Test
    void deveContarAsAvaliacoesQueOGabaritoNaoRotula() {
        RelatorioDeAcuracia relatorio = servicoCom(gabaritoDeQuatroLinhas()).avaliar(ORIGEM, GABARITO);

        assertThat(relatorio.avaliacoesProduzidas())
                .as("sete regras sobre um item")
                .isEqualTo(7);
        assertThat(relatorio.avaliacoesSemLinhaNoGabarito()).isEqualTo(3);
    }

    @Test
    void deveRegistrarContraQueOResultadoFoiObtido() {
        RelatorioDeAcuracia relatorio = servicoCom(gabaritoDeQuatroLinhas()).avaliar(ORIGEM, GABARITO);

        assertThat(relatorio.versaoDoCatalogo()).isEqualTo("catalogo-ficticio-0");
        assertThat(relatorio.versaoDoConjuntoDeRegras()).isEqualTo(ConjuntoRegras.VERSAO_PADRAO);
        assertThat(relatorio.documentosAuditados()).isEqualTo(1);
        assertThat(relatorio.itensAuditados()).isEqualTo(1);
    }

    @Test
    void medirSemDestinoNaoPodeGravarArquivoNenhum() {
        servicoCom(gabaritoDeQuatroLinhas()).avaliar(ORIGEM, GABARITO);

        assertThat(escritor.gravados).isEmpty();
    }

    @Test
    void medirComDestinoDeveGravarOMesmoRelatorioQueDevolve() {
        RelatorioDeAcuracia devolvido = servicoCom(gabaritoDeQuatroLinhas())
                .avaliarEGravar(ORIGEM, GABARITO, RELATORIO);

        assertThat(escritor.gravados).containsExactly(devolvido);
        assertThat(escritor.destinos).containsExactly(RELATORIO);
    }

    @Test
    void deveRecusarGabaritoSemNenhumaLinhaRotulada() {
        assertThatThrownBy(() -> servicoCom(new Gabarito(List.of())).avaliar(ORIGEM, GABARITO))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("aparência de medição feita");
    }

    @Test
    void deveRecusarMedicaoSemGabarito() {
        assertThatThrownBy(() -> servicoCom(gabaritoDeQuatroLinhas()).avaliar(ORIGEM, null))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("verdade de referência");
    }

    private ContagemDeAcuracia contagemDe(String regraId) {
        return servicoCom(gabaritoDeQuatroLinhas())
                .avaliar(ORIGEM, GABARITO)
                .daRegra(regraId)
                .orElseThrow()
                .contagem();
    }

    /** VP em R02, VN em R01, FN em R06 e um não avaliado em R05. */
    private static Gabarito gabaritoDeQuatroLinhas() {
        List<LinhaDeGabarito> linhas = new ArrayList<>(gabaritoSemALinhaNaoAvaliada().linhas());
        linhas.add(rotulo(5, RegraValorDeTributoConfere.ID, RotuloEsperado.ACHADO));
        return new Gabarito(linhas);
    }

    /** O mesmo gabarito, sem a linha que o motor não consegue julgar. */
    private static Gabarito gabaritoSemALinhaNaoAvaliada() {
        return new Gabarito(List.of(
                rotulo(2, RegraCstCompativelComClassificacao.ID, RotuloEsperado.ACHADO),
                rotulo(3, RegraClassificacaoTributariaExiste.ID, RotuloEsperado.CONFORME),
                rotulo(4, RegraNcmExiste.ID, RotuloEsperado.ACHADO)));
    }

    private static LinhaDeGabarito rotulo(int numeroDaLinha, String regraId, RotuloEsperado esperado) {
        return new LinhaDeGabarito(
                numeroDaLinha, new EnderecoDaAvaliacao(new ChaveAcesso(CHAVE), 1, regraId), esperado);
    }

    private ServicoDeAvaliacaoDeAcuracia servicoCom(Gabarito gabarito) {
        LoteDeDocumentos lote = new LoteDeDocumentos(
                "a".repeat(64),
                List.of(new DocumentoComItens(CenarioFicticio.documento(), List.of(item()))));

        return new ServicoDeAvaliacaoDeAcuracia(
                origem -> lote,
                ServicoDeAvaliacaoDeAcuraciaTest::catalogo,
                arquivo -> gabarito,
                new MotorAuditoria(),
                new ComparadorDeGabarito(),
                escritor,
                ToleranciaDeValor.de(new BigDecimal("0.01")));
    }

    /**
     * Catálogo que reconhece o código e o NCM do item, admite para o código um
     * CST diferente do declarado, e não traz alíquota nenhuma.
     */
    private static CatalogoParaAuditoria catalogo() {
        return new CatalogoParaAuditoria(
                "catalogo-ficticio-0",
                CenarioFicticio.coberturaTotal(),
                new RepositorioClassificacaoTributariaEmMemoria(List.of(
                        CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST))),
                new RepositorioNcmEmMemoria(List.of(CenarioFicticio.registroNcm(CenarioFicticio.NCM))),
                new RepositorioItemAnexoEmMemoria(List.of()),
                new RepositorioAliquotaEmMemoria(List.of()));
    }

    private static ItemDocumento item() {
        return ConstrutorDeItem.item()
                .numero(1)
                .ncm(CatalogoFicticio.NCM)
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST_ALTERNATIVO)
                .construir();
    }

    /** Guarda o que foi mandado gravar, para que o teste confira sem tocar em disco. */
    private static final class EscritorFalso implements EscritorDeRelatorioDeAcuracia {

        private final List<RelatorioDeAcuracia> gravados = new ArrayList<>();
        private final List<Path> destinos = new ArrayList<>();

        @Override
        public void escrever(RelatorioDeAcuracia relatorio, Path destino) {
            gravados.add(relatorio);
            destinos.add(destino);
        }

        @Override
        public String extensao() {
            return "csv";
        }
    }
}
