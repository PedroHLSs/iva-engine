package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Anexo citado em {@code anexosAdmitidos} tem de estar declarado em
 * {@code anexos-declarados.csv}; senão a carga inteira é recusada.
 *
 * <p>Até 30/09/2026 a conferência era contra {@code item-anexo.csv}. Desde
 * 01/10/2026 (decisão D9 do usuário) é contra a lista declarada: um anexo
 * declarado e carregado pode não ter linha nenhuma em {@code item-anexo.csv}
 * (anexo de NBS, por exemplo), e isso não recusa mais a carga.</p>
 *
 * <p>Valores fictícios: código {@code XXX000}, NCM {@code 00000000}, CST
 * {@code AAA}, anexos {@code ANEXO-XX} e {@code ANEXO-YY}, vigência em 1900.</p>
 */
class AnexoAdmitidoInexistenteNaCargaTest {

    private static final String CABECALHO_COMUM = "vigenciaInicio;vigenciaFim;fonteNormativa";
    private static final String VIGENCIA_FICTICIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String CABECALHO_DE_DADOS = CABECALHO_COMUM + ";natureza";
    private static final String LINHA_FICTICIA = VIGENCIA_FICTICIA + ";FICTICIO";

    @TempDir
    private Path diretorio;

    @Test
    void deveRecusarACargaQuandoOAnexoAdmitidoNaoFoiDeclarado() throws IOException {
        escreverCatalogo("ANEXO-YY", "ANEXO-XX");

        CargaRecusada recusa = catchThrowableOfType(
                () -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"), CargaRecusada.class);

        assertThat(recusa).describedAs("tudo ou nada: nada é gravado").isNotNull();
        assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
            assertThat(recusada.arquivo()).isEqualTo("classificacao-tributaria.csv");
            assertThat(recusada.comoTexto()).contains("XXX000", "ANEXO-YY", "anexos-declarados.csv");
        });
    }

    @Test
    void deveAceitarAnexoDeclaradoMesmoSemNenhumaLinhaEmItemAnexo() throws IOException {
        // A checagem de 30/09/2026 contra item-anexo.csv saiu: ANEXO-YY não tem linha lá e está declarado.
        // Até 03/10/2026 o ANEXO-YY era declarado de NCM. Desde então, anexo de NCM declarado carregado e sem linha é recusado pela guarda por anexo; de NBS, carregado e vazio de NCM, continua aceito (D7), que é o caso que este teste existe para mostrar.
        escreverCatalogo("ANEXO-YY", "ANEXO-YY", "NBS");

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.classificacoesTributarias()).singleElement()
                .satisfies(lida -> assertThat(lida.anexosAdmitidos()).isPresent());
    }

    @Test
    void naoDeveCobrarItemAnexoDeQuemDeclaraNenhum() throws IOException {
        escreverCatalogo("NENHUM", "ANEXO-XX");

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.classificacoesTributarias()).hasSize(1);
    }

    // Escreve os arquivos de uma carga fictícia, com o valor informado na coluna anexosAdmitidos e um anexo declarado.
    private void escreverCatalogo(String anexosAdmitidos, String anexoDeclarado) throws IOException {
        escreverCatalogo(anexosAdmitidos, anexoDeclarado, "NCM");
    }

    // Mesmo catálogo, com o tipo de código do anexo declarado informado. Acrescentado em 03/10/2026.
    private void escreverCatalogo(String anexosAdmitidos, String anexoDeclarado, String tipoDeCodigo) throws IOException {
        escrever("anexos-declarados.csv", """
                identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                %s;%s;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(anexoDeclarado, tipoDeCodigo));
        escrever("cobertura.csv", """
                tabela;%s;natureza
                CLASSIFICACAO_TRIBUTARIA;%s;FICTICIO
                NCM;%s;FICTICIO
                ITEM_ANEXO;%s;FICTICIO
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));
        escrever("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;Dispositivo ficticio;true;0;;%s;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA, anexosAdmitidos));
        escrever("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA));
        escrever("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA));
        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;%s
                CBS;99,99;ABRANGENCIA-XX;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA));
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
