package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura do diretório de catálogo.
 *
 * <p>Todos os valores usados aqui são explicitamente fictícios — código
 * {@code XXX000}, NCM {@code 00000000}, CST {@code AAA}, vigência em 1900 — e
 * nenhum deles afirma nada sobre a legislação. Os arquivos são escritos em
 * diretório temporário, e não versionados.</p>
 */
class LeitorDeCatalogoEmCsvTest {

    private static final String CABECALHO_COMUM = "vigenciaInicio;vigenciaFim;fonteNormativa";
    private static final String VIGENCIA_FICTICIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";

    @TempDir
    private Path diretorio;

    @BeforeEach
    void escreverCatalogoCompleto() throws IOException {
        escrever("cobertura.csv", """
                tabela;%s
                CLASSIFICACAO_TRIBUTARIA;%s
                NCM;%s
                ITEM_ANEXO;%s
                """.formatted(CABECALHO_COMUM,
                        VIGENCIA_FICTICIA, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));

        escrever("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s
                XXX000;AAA|BBB;Dispositivo ficticio;false;;;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA));

        escrever("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA));

        escrever("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA));

        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;%s
                CBS;99,99;ABRANGENCIA-XX;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA));
    }

    @Test
    void deveMontarACargaComAsQuatroTabelas() throws IOException {
        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.versao()).isEqualTo("carga-ficticia");
        assertThat(carga.classificacoesTributarias()).hasSize(1);
        assertThat(carga.registrosDeNcm()).hasSize(1);
        assertThat(carga.itensDeAnexo()).hasSize(1);
        assertThat(carga.aliquotas()).hasSize(1);
    }

    @Test
    void deveLerACoberturaDeclaradaDeCadaTabela() throws IOException {
        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.cobertura().ncm().vigenciaInicio()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(carga.cobertura().ncm().vigenciaFim()).contains(LocalDate.of(1900, 12, 31));
        assertThat(carga.cobertura().itensDeAnexo().fonteNormativa()).isEqualTo("FONTE FICTICIA v0.0");
    }

    @Test
    void deveAceitarTabelaDeclaradaSemRegistros() throws IOException {
        escrever("aliquota-vigente.csv",
                "tributo;percentual;abrangencia;%s%n".formatted(CABECALHO_COMUM));

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.aliquotas())
                .as("arquivo só com cabeçalho é a forma de dizer que a tabela não tem registros")
                .isEmpty();
    }

    @Test
    void deveRecusarArquivoAusente() throws IOException {
        Files.delete(diretorio.resolve("aliquota-vigente.csv"));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .as("arquivo ausente não pode ser lido como tabela vazia")
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("aliquota-vigente.csv");
    }

    @Test
    void deveRecusarCoberturaIncompleta() throws IOException {
        escrever("cobertura.csv", """
                tabela;%s
                CLASSIFICACAO_TRIBUTARIA;%s
                NCM;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("ITEM_ANEXO");
    }

    @Test
    void deveRecusarTabelaDesconhecidaNaCobertura() throws IOException {
        escrever("cobertura.csv", """
                tabela;%s
                CLASSIFICACAO_TRIBUTARIA;%s
                NCM;%s
                ITEM_ANEXO;%s
                TABELA_INVENTADA;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA,
                        VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("TABELA_INVENTADA");
    }

    @Test
    void deveRecusarDiretorioInexistente() {
        assertThatThrownBy(() ->
                LeitorDeCatalogoEmCsv.ler(diretorio.resolve("nao-existe"), "carga-ficticia"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class);
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
