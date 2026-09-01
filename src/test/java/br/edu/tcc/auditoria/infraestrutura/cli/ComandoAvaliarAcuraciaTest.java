package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.acuracia.ComparadorDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.EnderecoDaAvaliacao;
import br.edu.tcc.auditoria.aplicacao.acuracia.EscritorDeRelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.FonteDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.Gabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.LinhaDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.ServicoDeAvaliacaoDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.LoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;
import br.edu.tcc.auditoria.dominio.regras.RegraCstCompativelComClassificacao;
import br.edu.tcc.auditoria.dominio.regras.RegraValorDeTributoConfere;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.infraestrutura.acuracia.GabaritoInvalido;
import br.edu.tcc.auditoria.infraestrutura.acuracia.LeitorDeGabaritoCsv;
import br.edu.tcc.auditoria.infraestrutura.acuracia.TextoDeMetrica;
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
 * O que o comando imprime.
 *
 * <p>Usa o serviço de verdade, com dublês só para a leitura dos arquivos: o que
 * se confere aqui é o texto que chega a quem mede, e ele tem de sair de números
 * calculados pelo motor, não de um relatório montado à mão.</p>
 *
 * <p><strong>Todos os valores são fictícios.</strong></p>
 */
class ComandoAvaliarAcuraciaTest {

    private static final String CHAVE = CenarioFicticio.CHAVE_PRIMEIRA;
    private static final String OUTRA_CHAVE = CenarioFicticio.CHAVE_SEGUNDA;

    private final SaidaEmLista saida = new SaidaEmLista();
    private final EscritorFalso escritor = new EscritorFalso();

    @Test
    void deveImprimirAIdentificacaoDaRodadaAntesDosNumeros() {
        executar(gabaritoUsual());

        assertThat(saida.texto())
                .contains("Avaliação de acurácia")
                .contains("catálogo ........... catalogo-ficticio-0")
                .contains("conjunto de regras . 2026.1")
                .contains("documentos ......... 1")
                .contains("itens .............. 1");
    }

    @Test
    void deveImprimirUmaLinhaPorRegraEUmaConsolidada() {
        executar(gabaritoUsual());

        assertThat(saida.texto())
                .containsSubsequence("Regra", "R01", "R02", "R07", "CONSOLIDADO");
    }

    @Test
    void deveImprimirAsColunasQueNaoEntramNaMetricaSeparadasDasQueEntram() {
        executar(gabaritoUsual());

        assertThat(saida.texto())
                .containsSubsequence("VP", "FP", "FN", "VN", "Avaliados", "NaoAval", "SemAval", "Total",
                        "Precisão", "Recall", "F1");
    }

    @Test
    void deveExplicarPorQueNaoAvaliadoNaoContaComoAcertoNemComoErro() {
        executar(gabaritoUsual());

        assertThat(saida.texto())
                .contains("NaoAval e SemAval não entram em precisão, recall nem F1.")
                .contains("o motor não pôde julgar")
                .contains("Cobertura (avaliados / total do gabarito)");
    }

    @Test
    void deveEscreverMetricaIndefinidaEmVezDeDeixarEmBranco() {
        executar(gabaritoSoComALinhaNaoAvaliada());

        assertThat(saida.texto())
                .as("a regra R05 não julgou nada, e um campo vazio seria lido como zero")
                .contains(TextoDeMetrica.INDEFINIDA);
    }

    @Test
    void deveListarOsEnderecosDoGabaritoQueOMotorNaoAvaliou() {
        executar(gabaritoComDocumentoForaDoLote());

        assertThat(saida.texto())
                .contains("Linhas do gabarito que o motor não avaliou:")
                .contains("documento %s, item 1, regra %s"
                        .formatted(OUTRA_CHAVE, RegraCstCompativelComClassificacao.ID));
    }

    @Test
    void semRelatorioNaoDeveGravarNemAnunciarArquivo() {
        executar(gabaritoUsual());

        assertThat(escritor.destinos).isEmpty();
        assertThat(saida.texto()).doesNotContain("Relatório gravado em");
    }

    @Test
    void comRelatorioDeveGravarEAnunciarOCaminho() {
        comando(gabaritoUsual()).executar(Argumentos.de(
                ComandoAvaliarAcuracia.NOME,
                "--origem=origem-ficticia",
                "--gabarito=gabarito-ficticio.csv",
                "--relatorio=saida-ficticia.csv"));

        assertThat(escritor.destinos).containsExactly(Path.of("saida-ficticia.csv"));
        assertThat(saida.texto()).contains("Relatório gravado em");
    }

    @Test
    void gabaritoMalformadoDeveVirarErroDeUsoENaoRastroDePilha() {
        ComandoAvaliarAcuracia comando = comandoQueRecusaOGabarito();

        assertThatThrownBy(() -> comando.executar(Argumentos.de(
                ComandoAvaliarAcuracia.NOME,
                "--origem=origem-ficticia",
                "--gabarito=gabarito-ficticio.csv")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("Linha 7: rótulo fictício desconhecido");
    }

    @Test
    void deveExigirOrigemEGabarito() {
        assertThatThrownBy(() -> comando(gabaritoUsual())
                .executar(Argumentos.de(ComandoAvaliarAcuracia.NOME, "--origem=origem-ficticia")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("gabarito");
    }

    @Test
    void deveRecusarOpcaoQueNaoConhece() {
        assertThatThrownBy(() -> comando(gabaritoUsual()).executar(Argumentos.de(
                ComandoAvaliarAcuracia.NOME,
                "--origem=origem-ficticia",
                "--gabarito=gabarito-ficticio.csv",
                "--gravar-execucao")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("gravar-execucao");
    }

    @Test
    void oModoDeUsarDeveDizerOFormatoDoGabaritoEQueNaoAvaliadoNaoERotulo() {
        String modoDeUsar = comando(gabaritoUsual()).modoDeUsar();

        assertThat(modoDeUsar)
                .contains(LeitorDeGabaritoCsv.colunasEsperadas())
                .contains("ACHADO ou CONFORME")
                .contains("NAO_AVALIADO não é rótulo de gabarito")
                .contains("Nada é gravado no banco");
    }

    private void executar(Gabarito gabarito) {
        comando(gabarito).executar(Argumentos.de(
                ComandoAvaliarAcuracia.NOME,
                "--origem=origem-ficticia",
                "--gabarito=gabarito-ficticio.csv"));
    }

    private ComandoAvaliarAcuracia comando(Gabarito gabarito) {
        return new ComandoAvaliarAcuracia(servicoCom(arquivo -> gabarito), saida);
    }

    private ComandoAvaliarAcuracia comandoQueRecusaOGabarito() {
        return new ComandoAvaliarAcuracia(
                servicoCom(arquivo -> {
                    throw new GabaritoInvalido("Linha 7: rótulo fictício desconhecido.");
                }),
                saida);
    }

    private ServicoDeAvaliacaoDeAcuracia servicoCom(FonteDeGabarito fonteDeGabarito) {

        LoteDeDocumentos lote = new LoteDeDocumentos(
                "a".repeat(64),
                List.of(new DocumentoComItens(CenarioFicticio.documento(), List.of(item()))));

        return new ServicoDeAvaliacaoDeAcuracia(
                origem -> lote,
                ComandoAvaliarAcuraciaTest::catalogo,
                fonteDeGabarito,
                new MotorAuditoria(),
                new ComparadorDeGabarito(),
                escritor,
                ToleranciaDeValor.de(new BigDecimal("0.01")));
    }

    /** Rotula a regra que aponta e a que não consegue julgar. */
    private static Gabarito gabaritoUsual() {
        return new Gabarito(List.of(
                rotulo(2, CHAVE, RegraCstCompativelComClassificacao.ID, RotuloEsperado.ACHADO),
                rotulo(3, CHAVE, RegraValorDeTributoConfere.ID, RotuloEsperado.ACHADO)));
    }

    private static Gabarito gabaritoSoComALinhaNaoAvaliada() {
        return new Gabarito(List.of(
                rotulo(2, CHAVE, RegraValorDeTributoConfere.ID, RotuloEsperado.ACHADO)));
    }

    private static Gabarito gabaritoComDocumentoForaDoLote() {
        return new Gabarito(List.of(
                rotulo(2, CHAVE, RegraCstCompativelComClassificacao.ID, RotuloEsperado.ACHADO),
                rotulo(3, OUTRA_CHAVE, RegraCstCompativelComClassificacao.ID, RotuloEsperado.ACHADO)));
    }

    private static LinhaDeGabarito rotulo(
            int numeroDaLinha, String chave, String regraId, RotuloEsperado esperado) {

        return new LinhaDeGabarito(
                numeroDaLinha, new EnderecoDaAvaliacao(new ChaveAcesso(chave), 1, regraId), esperado);
    }

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

    private static final class SaidaEmLista implements Saida {

        private final List<String> linhas = new ArrayList<>();

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        String texto() {
            return String.join("\n", linhas);
        }
    }

    private static final class EscritorFalso implements EscritorDeRelatorioDeAcuracia {

        private final List<Path> destinos = new ArrayList<>();

        @Override
        public void escrever(RelatorioDeAcuracia relatorio, Path destino) {
            destinos.add(destino);
        }

        @Override
        public String extensao() {
            return "csv";
        }
    }
}
