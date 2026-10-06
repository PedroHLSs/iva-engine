package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O comportamento de carga usada contra carga rascunho, sem banco.
 *
 * <p>Dados fictícios: código {@code 999999}, CST {@code AAA}, NCM
 * {@code 00000000}, vigência em 1900. Nenhum afirma nada sobre a legislação.</p>
 */
class ServicoDeCargasTest {

    private static final ProcedenciaNormativa PROCEDENCIA =
            ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");

    private AcervoEmMemoria acervo;
    private ServicoDeCargas servico;

    @BeforeEach
    void preparar() {
        acervo = new AcervoEmMemoria();
        servico = new ServicoDeCargas(acervo, new ServicoDeImportacaoDeCatalogo(new RepositorioDeCargaDeCatalogo() {
            @Override
            public void salvar(CargaDeCatalogo carga) {
                acervo.gravar(carga, Optional.empty());
            }

            @Override
            public Optional<String> versaoDaCargaMaisRecente() {
                return Optional.empty();
            }
        }));
        servico.importar(carga("carga-a", "Dispositivo ficticio A"));
    }

    @Test
    void rascunhoDeveSerEditadoNoLugar() {
        PreviaDaEdicao previa = servico.previa("carga-a");
        assertThat(previa.efeito()).isEqualTo(EfeitoDaEdicao.ALTERAR_RASCUNHO);
        assertThat(previa.versaoQueSeraCriada()).isEmpty();

        ResultadoDaEdicao resultado = servico.editar("carga-a", substituicao("Dispositivo ficticio B"),
                EfeitoDaEdicao.ALTERAR_RASCUNHO, Optional.empty());

        assertThat(resultado.versaoResultante()).isEqualTo("carga-a");
        assertThat(acervo.conteudo("carga-a").orElseThrow().classificacoesTributarias().get(0).dispositivoLegal())
                .isEqualTo("Dispositivo ficticio B");
        assertThat(acervo.versoes()).containsExactly("carga-a");
    }

    @Test
    void rascunhoDeveSerExcluidoLivremente() {
        servico.excluir("carga-a");

        assertThat(acervo.versoes()).isEmpty();
    }

    @Test
    void cargaUsadaNaoDeveSerExcluidaEAMensagemDizQuantasAnalisesDependem() {
        acervo.selar("carga-a", 3);

        assertThatThrownBy(() -> servico.excluir("carga-a"))
                .isInstanceOf(CargaSelada.class)
                .hasMessageContaining("usada por 3 análise(s)");
        assertThat(acervo.versoes()).containsExactly("carga-a");
    }

    @Test
    void cargaSeladaSemAnaliseGravadaTambemNaoDeveSerExcluida() {
        acervo.selar("carga-a", 0);

        assertThatThrownBy(() -> servico.excluir("carga-a"))
                .isInstanceOf(CargaSelada.class)
                .hasMessageContaining("foi entregue a uma análise")
                .hasMessageContaining("relatórios exportados podem citá-la");
    }

    @Test
    void aPreviaDeCargaUsadaDeveDizerQuantasAnalisesEQualVersaoSeraCriada() {
        acervo.selar("carga-a", 2);

        PreviaDaEdicao previa = servico.previa("carga-a");

        assertThat(previa.efeito()).isEqualTo(EfeitoDaEdicao.CRIAR_VERSAO_NOVA);
        assertThat(previa.versaoQueSeraCriada()).contains("carga-a-ed1");
        assertThat(previa.aviso())
                .contains("usada por 2 análise(s)")
                .contains("Salvar criará a versão \"carga-a-ed1\"")
                .contains("continuará intacta");
    }

    @Test
    void editarCargaUsadaDeveCriarVersaoNovaEPreservarAOriginal() {
        acervo.selar("carga-a", 1);
        CargaDeCatalogo antes = acervo.conteudo("carga-a").orElseThrow();

        ResultadoDaEdicao resultado = servico.editar("carga-a", substituicao("Dispositivo ficticio B"),
                EfeitoDaEdicao.CRIAR_VERSAO_NOVA, Optional.of("carga-a-ed1"));

        assertThat(resultado.efeito()).isEqualTo(EfeitoDaEdicao.CRIAR_VERSAO_NOVA);
        assertThat(resultado.versaoResultante()).isEqualTo("carga-a-ed1");
        assertThat(acervo.conteudo("carga-a").orElseThrow()).isEqualTo(antes);
        assertThat(acervo.conteudo("carga-a-ed1").orElseThrow().classificacoesTributarias().get(0).dispositivoLegal())
                .isEqualTo("Dispositivo ficticio B");
        assertThat(acervo.origemDe("carga-a-ed1")).contains("carga-a");
        assertThat(acervo.conteudo("carga-a-ed1").orElseThrow().registrosDeNcm())
                .as("a tabela que não veio no envio é copiada da origem")
                .isEqualTo(antes.registrosDeNcm());
    }

    @Test
    void naoDeveGravarNadaSeACargaFoiSeladaEnquantoAEdicaoEstavaAberta() {
        PreviaDaEdicao vistaNaTela = servico.previa("carga-a");
        acervo.selar("carga-a", 1);

        assertThatThrownBy(() -> servico.editar("carga-a", substituicao("Dispositivo ficticio B"),
                vistaNaTela.efeito(), Optional.empty()))
                .isInstanceOf(EdicaoDesatualizada.class)
                .hasMessageContaining("Nada foi gravado");
        assertThat(acervo.conteudo("carga-a").orElseThrow().classificacoesTributarias().get(0).dispositivoLegal())
                .isEqualTo("Dispositivo ficticio A");
        assertThat(acervo.versoes()).containsExactly("carga-a");
    }

    @Test
    void naoDeveCriarVersaoComNomeQueJaExiste() {
        acervo.selar("carga-a", 1);
        servico.importar(carga("carga-b", "Dispositivo ficticio"));

        assertThatThrownBy(() -> servico.editar("carga-a", substituicao("Dispositivo ficticio B"),
                EfeitoDaEdicao.CRIAR_VERSAO_NOVA, Optional.of("carga-b")))
                .isInstanceOf(EdicaoDesatualizada.class)
                .hasMessageContaining("Já existe carga com a versão \"carga-b\"");
    }

    @Test
    void aEdicaoDeveAplicarAGuardaDeCoberturaSobreTabelaVazia() {
        SubstituicaoDeTabelas esvaziaNcm = new SubstituicaoDeTabelas(
                Optional.empty(),
                Optional.of(new TabelaSubstituta<>(List.of(), Optional.empty())),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> servico.editar("carga-a", esvaziaNcm,
                EfeitoDaEdicao.ALTERAR_RASCUNHO, Optional.empty()))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("cobertura sobre tabela sem nenhum registro")
                .hasMessageContaining("NCM");
    }

    @Test
    void aImportacaoDeveRecusarCoberturaDeclaradaSobreTabelaVazia() {
        CargaDeCatalogo semAnexo = new CargaDeCatalogo("carga-sem-anexo",
                new CoberturaDoCatalogo(PROCEDENCIA, PROCEDENCIA, PROCEDENCIA),
                new NaturezaDaCarga(Optional.of(Natureza.FICTICIO), Optional.of(Natureza.FICTICIO),
                        Optional.empty(), Optional.empty()),
                List.of(classificacao("Dispositivo ficticio")), List.of(ncm()), List.of(), List.of());

        assertThatThrownBy(() -> servico.importar(semAnexo))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("ITEM_ANEXO");
    }

    private static SubstituicaoDeTabelas substituicao(String dispositivo) {
        return new SubstituicaoDeTabelas(
                Optional.of(new TabelaSubstituta<>(List.of(classificacao(dispositivo)), Optional.of(Natureza.FICTICIO))),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static CargaDeCatalogo carga(String versao, String dispositivo) {
        return new CargaDeCatalogo(versao,
                new CoberturaDoCatalogo(PROCEDENCIA, PROCEDENCIA, PROCEDENCIA),
                new NaturezaDaCarga(Optional.of(Natureza.FICTICIO), Optional.of(Natureza.FICTICIO),
                        Optional.of(Natureza.FICTICIO), Optional.empty()),
                List.of(classificacao(dispositivo)), List.of(ncm()), List.of(anexo()), List.of());
    }

    private static ClassificacaoTributaria classificacao(String dispositivo) {
        return new ClassificacaoTributaria(new CodigoClassificacaoTributaria("999999"),
                Set.of(new CodigoCst("AAA")), dispositivo, false, Optional.empty(), List.of(), PROCEDENCIA);
    }

    private static RegistroNcm ncm() {
        return new RegistroNcm(new Ncm("00000000"), "Descricao ficticia", PROCEDENCIA);
    }

    private static ItemAnexo anexo() {
        return new ItemAnexo(new Ncm("00000000"), new IdentificadorAnexo("ANEXO-XX"), "TRATAMENTO-XX", PROCEDENCIA);
    }

    /** Acervo em memória, com selo e contagem de análises controlados pelo teste. */
    private static final class AcervoEmMemoria implements AcervoDeCargas {

        private final Map<String, CargaDeCatalogo> cargas = new LinkedHashMap<>();
        private final Map<String, Instant> selos = new LinkedHashMap<>();
        private final Map<String, Long> analises = new LinkedHashMap<>();
        private final Map<String, String> origens = new LinkedHashMap<>();

        void gravar(CargaDeCatalogo carga, Optional<String> origem) {
            cargas.put(carga.versao(), carga);
            origem.ifPresent(versao -> origens.put(carga.versao(), versao));
        }

        void selar(String versao, long quantasAnalises) {
            selos.put(versao, Instant.parse("1900-01-02T00:00:00Z"));
            analises.put(versao, quantasAnalises);
        }

        List<String> versoes() {
            return new ArrayList<>(cargas.keySet());
        }

        Optional<String> origemDe(String versao) {
            return Optional.ofNullable(origens.get(versao));
        }

        @Override
        public List<EstadoDaCarga> listar() {
            return cargas.keySet().stream().map(versao -> estado(versao).orElseThrow()).toList();
        }

        @Override
        public Optional<EstadoDaCarga> estado(String versao) {
            CargaDeCatalogo carga = cargas.get(versao);
            if (carga == null) {
                return Optional.empty();
            }
            List<String> ordem = versoes();
            return Optional.of(new EstadoDaCarga(versao, Instant.parse("1900-01-01T00:00:00Z"),
                    Optional.ofNullable(selos.get(versao)), analises.getOrDefault(versao, 0L),
                    Optional.ofNullable(origens.get(versao)), Optional.empty(),
                    ordem.get(ordem.size() - 1).equals(versao),
                    carga.classificacoesTributarias().size(), carga.registrosDeNcm().size(),
                    carga.itensDeAnexo().size(), carga.aliquotas().size(), carga.natureza()));
        }

        @Override
        public Optional<CargaDeCatalogo> conteudo(String versao) {
            return Optional.ofNullable(cargas.get(versao));
        }

        @Override
        public boolean existeVersao(String versao) {
            return cargas.containsKey(versao);
        }

        @Override
        public <T> T comCargaTravada(String versao, Function<EstadoDaCarga, T> operacao) {
            return operacao.apply(estado(versao).orElseThrow(() -> new CargaNaoEncontrada(versao)));
        }

        @Override
        public void substituirConteudo(String versao, CargaDeCatalogo nova) {
            if (selos.containsKey(versao)) {
                throw new CargaSelada("selada");
            }
            cargas.put(versao, nova);
        }

        @Override
        public void salvarDerivada(CargaDeCatalogo nova, String versaoDeOrigem) {
            gravar(nova, Optional.of(versaoDeOrigem));
        }

        @Override
        public void excluir(String versao) {
            if (selos.containsKey(versao)) {
                throw new CargaSelada("selada");
            }
            cargas.remove(versao);
        }
    }
}
