package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.SubstituicaoDeTabelas;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * O arquivo {@code anexos-declarados.csv} na carga (decisões do usuário D6, D7 e
 * D9, de 01/10/2026): é a lista de anexos válidos e diz em que período cada um
 * está carregado. A checagem de {@code anexosAdmitidos} passa a ser contra ele, e
 * não contra o que há em {@code item-anexo.csv}.
 *
 * <p>Valores fictícios: código {@code XXX000}, NCM {@code 00000000}, CST
 * {@code AAA}, anexos {@code ANEXO-XX}, {@code ANEXO-YY} e {@code ANEXO-ZZ},
 * vigência em 1900. Nenhum deles é afirmação sobre a legislação.</p>
 */
class AnexosDeclaradosNaCargaTest {

    private static final String CABECALHO_COMUM = "vigenciaInicio;vigenciaFim;fonteNormativa";
    private static final String VIGENCIA_FICTICIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String CABECALHO_DE_DADOS = CABECALHO_COMUM + ";natureza";
    private static final String LINHA_FICTICIA = VIGENCIA_FICTICIA + ";FICTICIO";
    private static final String CABECALHO_DOS_ANEXOS =
            "identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

    @TempDir
    private Path diretorio;

    @Test
    void deveRecusarAnexoAdmitidoForaDaListaDeclarada() throws IOException {
        // ANEXO-YY tem linha em item-anexo.csv, mas não foi declarado: a lista é a declarada.
        escreverCatalogo("ANEXO-YY", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        CargaRecusada recusa = recusaDaCarga();

        assertThat(recusa.recusadas()).singleElement()
                .satisfies(recusada -> assertThat(recusada.comoTexto()).contains("XXX000", "ANEXO-YY",
                        "anexos-declarados.csv"));
    }

    @Test
    void deveRecusarACargaSemArquivoDeAnexosQuandoAlgumCodigoCitaAnexo() throws IOException {
        escreverCatalogo("ANEXO-XX", true);

        CargaRecusada recusa = recusaDaCarga();

        assertThat(recusa.getMessage()).contains("anexos-declarados.csv", "XXX000");
    }

    @Test
    void deveAceitarACargaSemArquivoDeAnexosQuandoNenhumCodigoCitaAnexo() throws IOException {
        escreverCatalogo("NENHUM", true);

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.cobertura().anexosDeclarados()).isEmpty();
        // D021 (04/10/2026): cinco, com a cobertura, que passou a declarar natureza. Até essa data eram quatro.
        assertThat(carga.natureza().declaradas()).hasSize(5);
    }

    @Test
    void deveAceitarAnexoDeclaradoCarregadoESemNenhumaLinhaEmItemAnexo() throws IOException {
        // D7: ANEXO-ZZ, de NBS, está carregado e vazio de NCM.
        escreverCatalogo("ANEXO-ZZ", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-ZZ;NBS;1900-01-01;1900-12-31;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-YY;NCM_E_NBS;;;FONTE FICTICIA v0.0;FICTICIO\n");

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.cobertura().anexosDeclarados()).hasSize(3);
        AnexoDeclarado zz = carga.cobertura().anexosDeclarados().stream()
                .filter(anexo -> anexo.identificador().valor().equals("ANEXO-ZZ")).findFirst().orElseThrow();
        assertThat(zz.tipoDeCodigo()).isEqualTo(TipoDeCodigoDoAnexo.NBS);
        assertThat(zz.carregamento()).isPresent();
        AnexoDeclarado yy = carga.cobertura().anexosDeclarados().stream()
                .filter(anexo -> anexo.identificador().valor().equals("ANEXO-YY")).findFirst().orElseThrow();
        assertThat(yy.carregamento()).describedAs("sem vigência: existe, mas não está carregado").isEmpty();
        assertThat(carga.natureza().declaradas())
                .containsEntry("ANEXO_DECLARADO", Natureza.FICTICIO)
                // D021 (04/10/2026): seis, com a cobertura. Até essa data eram cinco.
                .hasSize(6);
    }

    @Test
    void deveRecusarTipoDeCodigoForaDoFormato() throws IOException {
        escreverCatalogo("ANEXO-XX", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;LC 999/1900;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        CargaRecusada recusa = recusaDaCarga();

        assertThat(recusa.recusadas()).anySatisfy(recusada -> {
            assertThat(recusada.arquivo()).isEqualTo("anexos-declarados.csv");
            assertThat(recusada.coluna()).contains("tipoDeCodigo");
            assertThat(recusada.valor()).contains("LC 999/1900");
        });
    }

    @Test
    void deveRecusarFimDeVigenciaSemInicio() throws IOException {
        escreverCatalogo("ANEXO-XX", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;NCM;;1900-12-31;FONTE FICTICIA v0.0;FICTICIO\n");

        CargaRecusada recusa = recusaDaCarga();

        assertThat(recusa.recusadas()).anySatisfy(recusada -> {
            assertThat(recusada.arquivo()).isEqualTo("anexos-declarados.csv");
            assertThat(recusada.coluna()).contains("vigenciaInicio");
        });
    }

    @Test
    void deveRecusarAnexoDeclaradoDuasVezes() throws IOException {
        escreverCatalogo("ANEXO-XX", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-XX;NBS;;;FONTE FICTICIA v0.0;FICTICIO\n");

        CargaRecusada recusa = recusaDaCarga();

        assertThat(recusa.recusadas()).anySatisfy(recusada -> {
            assertThat(recusada.linha()).contains(3);
            assertThat(recusada.coluna()).contains("identificadorDoAnexo");
        });
    }

    @Test
    void aSubstituicaoComIdentificadorForaDaListaDeveSerRecusada() throws IOException {
        FontesDoCatalogo envio = FontesDoCatalogo.emMemoria(Map.of(
                "classificacao-tributaria.csv", classificacao("ANEXO-YY").getBytes(StandardCharsets.UTF_8),
                "anexos-declarados.csv", (CABECALHO_DOS_ANEXOS + "\n"
                        + "ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n").getBytes(StandardCharsets.UTF_8)));

        CargaRecusada recusa = catchThrowableOfType(
                () -> LeitorDeCatalogoEmCsv.lerSubstituicao(envio), CargaRecusada.class);

        assertThat(recusa).describedAs("tudo ou nada, também na edição").isNotNull();
        assertThat(recusa.getMessage()).contains("ANEXO-YY");
    }

    @Test
    void aSubstituicaoSoDaClassificacaoDeveSerConferidaContraAListaDaOrigem() throws IOException {
        escreverCatalogo("ANEXO-XX", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");
        CargaDeCatalogo origem = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");
        SubstituicaoDeTabelas soAClassificacao = LeitorDeCatalogoEmCsv.lerSubstituicao(
                FontesDoCatalogo.emMemoria(Map.of("classificacao-tributaria.csv",
                        classificacao("ANEXO-YY").getBytes(StandardCharsets.UTF_8))));

        CatalogoInvalido recusa = catchThrowableOfType(
                () -> soAClassificacao.aplicarSobre(origem, "carga-ficticia-2"), CatalogoInvalido.class);

        assertThat(recusa).describedAs("a lista declarada vem da carga de origem").isNotNull();
        assertThat(recusa.getMessage()).contains("XXX000", "ANEXO-YY");
    }

    @Test
    void aSubstituicaoQueNaoTrazOsAnexosDeveHerdarAListaDaOrigem() throws IOException {
        escreverCatalogo("ANEXO-XX", true);
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n"
                + "ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");
        CargaDeCatalogo origem = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");
        SubstituicaoDeTabelas soONcm = LeitorDeCatalogoEmCsv.lerSubstituicao(
                FontesDoCatalogo.emMemoria(Map.of("registro-ncm.csv", ("ncm;descricao;" + CABECALHO_DE_DADOS
                        + "\n00000000;Outra descricao ficticia;" + LINHA_FICTICIA + "\n")
                        .getBytes(StandardCharsets.UTF_8))));

        CargaDeCatalogo nova = soONcm.aplicarSobre(origem, "carga-ficticia-2");

        assertThat(nova.cobertura().anexosDeclarados()).hasSize(1);
        assertThat(nova.natureza().declaradas()).containsKey("ANEXO_DECLARADO");
    }

    // Escreve os cinco arquivos obrigatórios; o código fictício admite o valor informado.
    private void escreverCatalogo(String anexosAdmitidos, boolean comItemAnexoYy) throws IOException {
        escrever("cobertura.csv", """
                tabela;%s;natureza
                CLASSIFICACAO_TRIBUTARIA;%s;FICTICIO
                NCM;%s;FICTICIO
                ITEM_ANEXO;%s;FICTICIO
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));
        escrever("classificacao-tributaria.csv", classificacao(anexosAdmitidos));
        escrever("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA));
        escrever("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA)
                + (comItemAnexoYy ? "00000000;ANEXO-YY;TRATAMENTO-YY;" + LINHA_FICTICIA + "\n" : ""));
        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;%s
                CBS;99,99;ABRANGENCIA-XX;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA));
    }

    private static String classificacao(String anexosAdmitidos) {
        return """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;Dispositivo ficticio;true;0;;%s;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA, anexosAdmitidos);
    }

    private CargaRecusada recusaDaCarga() {
        CargaRecusada recusa = catchThrowableOfType(
                () -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"), CargaRecusada.class);
        assertThat(recusa).describedAs("a carga deveria ter sido recusada inteira").isNotNull();
        return recusa;
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
