package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;
import br.edu.tcc.auditoria.dominio.regras.RegraCstCompativelComClassificacao;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioAliquotaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioClassificacaoTributariaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioItemAnexoEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioNcmEmMemoria;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O serviço de auditoria é orquestração: lê o lote, carrega o catálogo, monta o
 * conjunto de regras com a cobertura declarada, roda o motor e grava. Estes
 * testes usam dublês para a leitura e para a gravação, e o motor e o catálogo de
 * verdade — é na junção dos três que os erros aparecem.
 */
class ServicoDeAuditoriaTest {

    private static final String HASH_DA_ENTRADA = "a".repeat(64);
    private static final Instant MOMENTO = Instant.parse("1900-01-01T12:00:00Z");
    private static final Path ORIGEM = Path.of("origem-ficticia");

    private RepositorioDaAuditoriaFalso repositorio;

    @BeforeEach
    void prepararRepositorio() {
        repositorio = new RepositorioDaAuditoriaFalso();
    }

    @Test
    void deveGravarOResultadoDaRodada() {
        ItemDocumento item = itemComCstIncompativel();
        servicoCom(item).auditar(ORIGEM);

        assertThat(repositorio.gravados).hasSize(1);
    }

    @Test
    void deveRegistrarNaExecucaoOQueEntrouEComQueFoiConfrontado() {
        ItemDocumento item = itemComCstIncompativel();

        var resultado = servicoCom(item).auditar(ORIGEM);

        assertThat(resultado.execucao().hashEntrada()).isEqualTo(HASH_DA_ENTRADA);
        assertThat(resultado.execucao().versaoCatalogo()).isEqualTo("catalogo-ficticio");
        assertThat(resultado.execucao().versaoConjuntoRegras()).isNotBlank();
        assertThat(resultado.execucao().dataHora()).isEqualTo(MOMENTO);
        assertThat(resultado.execucao().quantidadeDocumentos()).isEqualTo(1);
        assertThat(resultado.execucao().quantidadeItens()).isEqualTo(1);
    }

    @Test
    void deveLigarCadaApontamentoAoResumoDoItemQueOOriginou() {
        ItemDocumento item = itemComCstIncompativel();

        var resultado = servicoCom(item).auditar(ORIGEM);

        assertThat(resultado.achados())
                .as("o apontamento precisa carregar o resumo do item para a tratativa sobreviver "
                        + "ao reprocessamento")
                .isNotEmpty()
                .allSatisfy(localizado -> assertThat(localizado.hashDoItem())
                        .isEqualTo(HashDoItem.de(CenarioFicticio.documento().chaveAcesso(), item)));
    }

    @Test
    void deveApontarCstIncompativelComAClassificacao() {
        var resultado = servicoCom(itemComCstIncompativel()).auditar(ORIGEM);

        assertThat(resultado.achados())
                .extracting(localizado -> localizado.achado().regraId())
                .contains(RegraCstCompativelComClassificacao.ID);
        assertThat(resultado.execucao().achadosDe(Severidade.MODERADA)).isEqualTo(1);
    }

    @Test
    void deveContarAsAvaliacoesQueNaoConcluiram() {
        var resultado = servicoCom(itemComCstIncompativel()).auditar(ORIGEM);

        assertThat(resultado.quantidadeDeNaoAvaliadas())
                .as("sem alíquota no catálogo, a regra de valor não conclui — e isso não é conformidade")
                .isPositive();
        assertThat(resultado.quantidadeDeAvaliacoes())
                .isEqualTo(resultado.quantidadeDeConformes()
                        + resultado.quantidadeDeNaoAvaliadas()
                        + resultado.achados().size());
    }

    @Test
    void deveProduzirOMesmoResultadoAoRodarDuasVezes() {
        ServicoDeAuditoria servico = servicoCom(itemComCstIncompativel());

        var primeira = servico.auditar(ORIGEM);
        var segunda = servico.auditar(ORIGEM);

        assertThat(segunda.execucao().hashEntrada()).isEqualTo(primeira.execucao().hashEntrada());
        assertThat(segunda.execucao().id())
                .as("cada rodada é um fato distinto, com identificador próprio")
                .isNotEqualTo(primeira.execucao().id());
        assertThat(segunda.achados()).hasSameSizeAs(primeira.achados());
        assertThat(segunda.achados().get(0).hashDoItem())
                .isEqualTo(primeira.achados().get(0).hashDoItem());
    }

    @Test
    void deveExigirOrigem() {
        assertThatThrownBy(() -> servicoCom(itemComCstIncompativel()).auditar(null))
                .isInstanceOf(AuditoriaInvalida.class);
    }

    private ServicoDeAuditoria servicoCom(ItemDocumento item) {
        Documento documento = CenarioFicticio.documento();
        LoteDeDocumentos lote = new LoteDeDocumentos(
                HASH_DA_ENTRADA, List.of(new DocumentoComItens(documento, List.of(item))));

        return new ServicoDeAuditoria(
                origem -> lote,
                ServicoDeAuditoriaTest::catalogo,
                repositorio,
                new MotorAuditoria(),
                ToleranciaDeValor.de(new BigDecimal("0.01")),
                Clock.fixed(MOMENTO, ZoneOffset.UTC));
    }

    /**
     * Catálogo fictício que reconhece o código e o NCM do item, mas admite para o
     * código um CST diferente do que o item declara.
     */
    private static CatalogoParaAuditoria catalogo() {
        return new CatalogoParaAuditoria(
                "catalogo-ficticio",
                CenarioFicticio.coberturaTotal(),
                new RepositorioClassificacaoTributariaEmMemoria(List.of(
                        CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST))),
                new RepositorioNcmEmMemoria(List.of(
                        CenarioFicticio.registroNcm(CenarioFicticio.NCM))),
                new RepositorioItemAnexoEmMemoria(List.of()),
                new RepositorioAliquotaEmMemoria(List.of()));
    }

    private static ItemDocumento itemComCstIncompativel() {
        return ConstrutorDeItem.item()
                .numero(1)
                .ncm(CatalogoFicticio.NCM)
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST_ALTERNATIVO)
                .construir();
    }

    /** Guarda o que foi mandado gravar, para que o teste confira sem banco. */
    private static final class RepositorioDaAuditoriaFalso implements RepositorioDaAuditoria {

        private final List<ResultadoDaAuditoria> gravados = new ArrayList<>();

        @Override
        public void persistir(ResultadoDaAuditoria resultado) {
            gravados.add(resultado);
        }
    }
}
