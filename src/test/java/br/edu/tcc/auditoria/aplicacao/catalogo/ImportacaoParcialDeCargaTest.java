package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas.OrigemVista;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.infraestrutura.catalogo.CargaRecusada;
import br.edu.tcc.auditoria.infraestrutura.catalogo.FontesDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D026 (04/10/2026): a importação parcial no serviço de cargas, com o acervo em memória e os arquivos lidos pelo mesmo leitor de CSV da web e da linha de comando. Depois da primeira carga, um arquivo basta: o que não veio é copiado da carga mais recente, que fica intacta, e o resultado é uma carga nova. A origem vista é conferida pela versão e pelos dois instantes; se mudou, nada é gravado. Valores fictícios.
class ImportacaoParcialDeCargaTest {

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

    private AcervoDeCargasEmMemoriaParaTeste acervo;
    private ServicoDeCargas servico;

    @BeforeEach
    void preparar() {
        acervo = new AcervoDeCargasEmMemoriaParaTeste();
        servico = new ServicoDeCargas(acervo, new ServicoDeImportacaoDeCatalogo(acervo));
    }

    @Test
    void umArquivoComAcervoVazioDeveSerRecusadoComAMensagemDeArquivoAusente() {
        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                Optional.empty()))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("Falta o arquivo \"classificacao-tributaria.csv\"")
                .hasMessageContaining("Falta o arquivo \"cobertura.csv\"")
                .hasMessageContaining("Arquivo ausente não é lido como tabela vazia");
        assertThat(acervo.listar()).isEmpty();
    }

    @Test
    void umArquivoComErroNoAcervoVazioDeveTrazerTambemOsArquivosAusentes() {
        assertThatThrownBy(() -> parcial("carga-b",
                Map.of("aliquota-vigente.csv", "tributo;percentual;abrangencia;" + DADOS + "\nCBS;nao-e-numero;X;"
                        + VIGENCIA + ";FICTICIO\n"),
                Optional.empty()))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("aliquota-vigente.csv")
                .hasMessageContaining("Falta o arquivo \"registro-ncm.csv\"");
        assertThat(acervo.listar()).isEmpty();
    }

    @Test
    void parcialComOrigemCorretaDeveCriarAVersaoDigitadaComDerivadaDe() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        CargaDeCatalogo origem = acervo.conteudo("carga-a").orElseThrow();

        ResultadoDaImportacao resultado = parcial("carga-b",
                Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vista("carga-a"));

        CargaDeCatalogo nova = acervo.conteudo("carga-b").orElseThrow();
        assertThat(resultado.tipo()).isEqualTo(TipoDaImportacao.PARCIAL);
        assertThat(resultado.origem().orElseThrow().versao()).isEqualTo("carga-a");
        assertThat(acervo.origemDe("carga-b")).contains("carga-a");
        assertThat(resultado.tabelasEnviadas()).containsExactly(NaturezaDaCarga.ALIQUOTAS);
        assertThat(resultado.tabelasHerdadas()).containsExactly(NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS,
                NaturezaDaCarga.REGISTROS_DE_NCM, NaturezaDaCarga.ITENS_DE_ANEXO, NaturezaDaCarga.COBERTURA,
                NaturezaDaCarga.ANEXOS_DECLARADOS);
        assertThat(nova.classificacoesTributarias()).isEqualTo(origem.classificacoesTributarias());
        assertThat(nova.registrosDeNcm()).isEqualTo(origem.registrosDeNcm());
        assertThat(nova.itensDeAnexo()).isEqualTo(origem.itensDeAnexo());
        assertThat(nova.cobertura()).isEqualTo(origem.cobertura());
        assertThat(nova.aliquotas()).hasSize(1);
        assertThat(acervo.estado("carga-b").orElseThrow().maisRecente()).isTrue();
    }

    @Test
    void parcialDeveHerdarDaMaisRecenteENaoDeOutraCarga() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        importarCompleta("carga-c", catalogo("Dispositivo ficticio C"));

        parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vista("carga-c"));

        assertThat(acervo.origemDe("carga-b")).contains("carga-c");
        assertThat(acervo.conteudo("carga-b").orElseThrow().classificacoesTributarias())
                .isEqualTo(acervo.conteudo("carga-c").orElseThrow().classificacoesTributarias());
        assertThat(acervo.conteudo("carga-b").orElseThrow().classificacoesTributarias().get(0).dispositivoLegal())
                .isEqualTo("Dispositivo ficticio C");
    }

    @Test
    void acervoQueFicaVazioAteATravaDeveExigirOsCincoDentroDaTrava() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        Optional<OrigemVista> vistaDaA = vista("carga-a");
        acervo.esvaziarNaProximaTrava();

        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vistaDaA))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("Falta o arquivo \"classificacao-tributaria.csv\"");
        assertThat(acervo.listar()).isEmpty();
    }

    @Test
    void asTabelasHerdadasDevemManterANaturezaDaOrigem() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));

        parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("NORMATIVO")), vista("carga-a"));

        NaturezaDaCarga natureza = acervo.conteudo("carga-b").orElseThrow().natureza();
        assertThat(natureza.aliquotas()).contains(Natureza.NORMATIVO);
        assertThat(natureza.classificacoesTributarias()).contains(Natureza.FICTICIO);
        assertThat(natureza.registrosDeNcm()).contains(Natureza.FICTICIO);
        assertThat(natureza.itensDeAnexo()).contains(Natureza.FICTICIO);
        assertThat(natureza.cobertura()).contains(Natureza.FICTICIO);
        assertThat(natureza.situacao()).isEqualTo(SituacaoDaNatureza.PARCIALMENTE_FICTICIO);
    }

    @Test
    void aOrigemRascunhoDeveContinuarIntactaDepoisDaParcial() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        CargaDeCatalogo antes = acervo.conteudo("carga-a").orElseThrow();

        parcial("carga-b", trocaDeClassificacao("Dispositivo ficticio B"), vista("carga-a"));

        assertThat(acervo.conteudo("carga-a").orElseThrow()).isEqualTo(antes);
        assertThat(acervo.estado("carga-a").orElseThrow().alteradaEm()).isEmpty();
        assertThat(acervo.estado("carga-a").orElseThrow().selada()).isFalse();
        assertThat(acervo.conteudo("carga-b").orElseThrow().classificacoesTributarias().get(0).dispositivoLegal())
                .isEqualTo("Dispositivo ficticio B");
    }

    @Test
    void parcialSemOrigemEsperadaDeveSerRecusadaSemGravar() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));

        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                Optional.empty()))
                .isInstanceOfSatisfying(OrigemDaImportacaoNaoInformada.class,
                        recusa -> assertThat(recusa.origemAtual().versao()).isEqualTo("carga-a"))
                .hasMessageContaining("\"carga-a\"")
                .hasMessageContaining("Nada foi gravado");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void parcialSemOInstanteDeImportacaoDaOrigemDeveSerRecusadaSemGravar() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));

        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                Optional.of(OrigemVista.pelaTela("carga-a", Optional.empty(), Optional.empty()))))
                .isInstanceOf(OrigemDaImportacaoNaoInformada.class)
                .hasMessageContaining("instante de importação");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void parcialComOrigemQueNaoEhAMaisRecenteDeveSerRecusadaSemGravarEDizerAAtual() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        Optional<OrigemVista> vistaDaA = vista("carga-a");
        importarCompleta("carga-c", catalogo("Dispositivo ficticio C"));

        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vistaDaA))
                .isInstanceOfSatisfying(OrigemDaImportacaoMudou.class,
                        recusa -> assertThat(recusa.origemAtual().versao()).isEqualTo("carga-c"))
                .hasMessageContaining("\"carga-c\"");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void parcialComOrigemAlteradaDepoisDaTelaDeveSerRecusadaSemGravar() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        Optional<OrigemVista> vistaAntesDeAlterar = vista("carga-a");
        servico.editar("carga-a", substituicao(trocaDeClassificacao("Dispositivo ficticio alterado")),
                EfeitoDaEdicao.ALTERAR_RASCUNHO, Optional.empty());

        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                vistaAntesDeAlterar))
                .isInstanceOf(OrigemDaImportacaoMudou.class)
                .hasMessageContaining("foi alterada depois que a tela a mostrou");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void origemExcluidaEImportadaDeNovoComOMesmoNomeDeveSerRecusadaSemGravar() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        Optional<OrigemVista> vistaDaPrimeira = vista("carga-a");
        servico.excluir("carga-a");
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A de novo"));

        assertThatThrownBy(() -> parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")),
                vistaDaPrimeira))
                .isInstanceOf(OrigemDaImportacaoMudou.class)
                .hasMessageContaining("excluída e importada de novo");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void versaoDigitadaQueJaExisteDeveSerRecusada() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        CargaDeCatalogo antes = acervo.conteudo("carga-a").orElseThrow();

        assertThatThrownBy(() -> parcial("carga-a", Map.of("aliquota-vigente.csv", aliquotas("NORMATIVO")),
                vista("carga-a")))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("Já existe carga de catálogo com a versão \"carga-a\"");
        assertThat(acervo.conteudo("carga-a").orElseThrow()).isEqualTo(antes);
    }

    @Test
    void cincoArquivosComOrigemInformadaDevemImportarCompletoEDizerQueAOrigemNaoFoiUsada() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));

        ResultadoDaImportacao resultado = servico.importarCompleta(
                ler("carga-b", catalogo("Dispositivo ficticio B")), Optional.of("carga-a"));

        assertThat(resultado.tipo()).isEqualTo(TipoDaImportacao.COMPLETA);
        assertThat(resultado.tabelasHerdadas()).isEmpty();
        assertThat(resultado.origem()).isEmpty();
        assertThat(resultado.origemInformadaNaoUsada()).contains("carga-a");
        assertThat(resultado.aviso()).contains("nenhuma tabela foi herdada", "\"carga-a\", não foi usada",
                "anexos-declarados.csv não veio");
        assertThat(acervo.origemDe("carga-b")).isEmpty();
        assertThat(acervo.conteudo("carga-b").orElseThrow().classificacoesTributarias().get(0).dispositivoLegal())
                .isEqualTo("Dispositivo ficticio B");
    }

    @Test
    void parcialQueViolaAGuardaDeCoberturaDeveSerRecusadaInteira() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("registro-ncm.csv", "ncm;descricao;" + DADOS + "\n");
        arquivos.put("cobertura.csv", catalogo("x").get("cobertura.csv"));

        assertThatThrownBy(() -> parcial("carga-b", arquivos, vista("carga-a")))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("cobertura sobre tabela sem nenhum registro")
                .hasMessageContaining("NCM");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void parcialComAnexoHerdadoQueNaoDeclaraOCodigoDeveSerRecusada() {
        Map<String, String> comAnexos = catalogo("Dispositivo ficticio A");
        comAnexos.put("anexos-declarados.csv", anexosDeclarados());
        importarCompleta("carga-a", comAnexos);
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("classificacao-tributaria.csv", classificacao("Dispositivo ficticio B", "ANEXO-ZZ"));
        arquivos.put("cobertura.csv", comAnexos.get("cobertura.csv"));

        assertThatThrownBy(() -> parcial("carga-b", arquivos, vista("carga-a")))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("anexos-declarados.csv")
                .hasMessageContaining("ANEXO-ZZ");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void parcialQueTrocaTabelaComCoberturaSemCoberturaJuntoDeveSerRecusada() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));

        assertThatThrownBy(() -> parcial("carga-b",
                Map.of("classificacao-tributaria.csv", classificacao("Dispositivo ficticio B", "NENHUM")),
                vista("carga-a")))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("exige cobertura.csv junto");
        assertThat(acervo.existeVersao("carga-b")).isFalse();
    }

    @Test
    void aEdicaoDeveExigirCoberturaJuntoComTabelaComCobertura() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        CargaDeCatalogo antes = acervo.conteudo("carga-a").orElseThrow();

        assertThatThrownBy(() -> servico.editar("carga-a",
                substituicao(Map.of("classificacao-tributaria.csv", classificacao("Dispositivo ficticio B", "NENHUM"))),
                EfeitoDaEdicao.ALTERAR_RASCUNHO, Optional.empty()))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("exige cobertura.csv junto");
        assertThat(acervo.conteudo("carga-a").orElseThrow()).isEqualTo(antes);
    }

    @Test
    void todoCaminhoQueMudaACargaMaisRecenteDeveTomarATravaDoAcervo() {
        importarCompleta("carga-a", catalogo("Dispositivo ficticio A"));
        assertThat(acervo.travasDoAcervo()).describedAs("importação completa").isEqualTo(1);

        parcial("carga-b", Map.of("aliquota-vigente.csv", aliquotas("FICTICIO")), vista("carga-a"));
        assertThat(acervo.travasDoAcervo()).describedAs("importação parcial").isEqualTo(2);

        servico.editar("carga-b", substituicao(trocaDeClassificacao("Dispositivo ficticio B")),
                EfeitoDaEdicao.ALTERAR_RASCUNHO, Optional.empty());
        assertThat(acervo.travasDoAcervo()).describedAs("edição no lugar").isEqualTo(3);

        acervo.selar("carga-b");
        servico.editar("carga-b", substituicao(trocaDeClassificacao("Dispositivo ficticio C")),
                EfeitoDaEdicao.CRIAR_VERSAO_NOVA, Optional.of("carga-b-ed1"));
        assertThat(acervo.travasDoAcervo()).describedAs("edição que cria versão").isEqualTo(4);

        servico.excluir("carga-b-ed1");
        assertThat(acervo.travasDoAcervo()).describedAs("exclusão").isEqualTo(5);
    }

    // Os cinco arquivos obrigatórios; o código fictício não admite anexo.
    static Map<String, String> catalogo(String dispositivo) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", classificacao(dispositivo, "NENHUM"));
        arquivos.put("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("aliquota-vigente.csv", "tributo;percentual;abrangencia;" + DADOS + "\n");
        return arquivos;
    }

    static String classificacao(String dispositivo, String anexosAdmitidos) {
        return """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;%s;false;;;%s;FICTICIO;%s
                """.formatted(DADOS, dispositivo, VIGENCIA, anexosAdmitidos);
    }

    static String aliquotas(String natureza) {
        return "tributo;percentual;abrangencia;" + DADOS + "\nCBS;99,99;ABRANGENCIA-XX;" + VIGENCIA + ";"
                + natureza + "\n";
    }

    static String anexosDeclarados() {
        return """
                identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                """;
    }

    // A troca da tabela de classificações, com a cobertura junto, como a decisão D-a exige.
    private static Map<String, String> trocaDeClassificacao(String dispositivo) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("classificacao-tributaria.csv", classificacao(dispositivo, "NENHUM"));
        arquivos.put("cobertura.csv", catalogo(dispositivo).get("cobertura.csv"));
        return arquivos;
    }

    private Optional<OrigemVista> vista(String versao) {
        EstadoDaCarga estado = acervo.estado(versao).orElseThrow();
        return Optional.of(OrigemVista.pelaTela(versao, Optional.of(estado.importadoEm()), estado.alteradaEm()));
    }

    private void importarCompleta(String versao, Map<String, String> arquivos) {
        servico.importarCompleta(ler(versao, arquivos), Optional.empty());
    }

    private ResultadoDaImportacao parcial(String versao, Map<String, String> arquivos, Optional<OrigemVista> vista) {
        FontesDoCatalogo fontes = fontes(arquivos);
        return servico.importarParcial(versao, () -> substituicao(arquivos), vista,
                versaoLida -> lerFontes(fontes, versaoLida));
    }

    private static CargaDeCatalogo ler(String versao, Map<String, String> arquivos) {
        return lerFontes(fontes(arquivos), versao);
    }

    private static CargaDeCatalogo lerFontes(FontesDoCatalogo fontes, String versao) {
        try {
            return LeitorDeCatalogoEmCsv.ler(fontes, versao);
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }

    private static SubstituicaoDeTabelas substituicao(Map<String, String> arquivos) {
        try {
            return LeitorDeCatalogoEmCsv.lerSubstituicao(fontes(arquivos));
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }

    private static FontesDoCatalogo fontes(Map<String, String> arquivos) {
        Map<String, byte[]> porNome = new LinkedHashMap<>();
        arquivos.forEach((nome, conteudo) -> porNome.put(nome, conteudo.getBytes(StandardCharsets.UTF_8)));
        return FontesDoCatalogo.emMemoria(porNome);
    }
}
