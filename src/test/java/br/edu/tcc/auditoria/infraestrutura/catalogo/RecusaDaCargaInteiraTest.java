package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.SubstituicaoDeTabelas;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A carga é recusada inteira, com todas as linhas problemáticas de todos os
 * arquivos numa mensagem só. Acrescentado na Etapa 12.
 *
 * <p>Até a Etapa 11 a importação parava na primeira linha recusada: quem
 * importava corrigia uma, reimportava, e descobria a próxima. Aqui cada teste
 * planta mais de um problema, em linhas e arquivos diferentes, e confere que
 * todos aparecem, cada um com linha, coluna e valor.</p>
 *
 * <p>Valores fictícios: código {@code XXX000}, NCM {@code 00000000}, CST
 * {@code AAA}, vigência em 1900.</p>
 */
class RecusaDaCargaInteiraTest {

    private static final String CABECALHO_COMUM = "vigenciaInicio;vigenciaFim;fonteNormativa";
    private static final String VIGENCIA_FICTICIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String CABECALHO_DE_DADOS = CABECALHO_COMUM + ";natureza";
    private static final String LINHA_FICTICIA = VIGENCIA_FICTICIA + ";FICTICIO";

    @TempDir
    private Path diretorio;

    @BeforeEach
    void escreverCatalogoValido() throws IOException {
        escrever("cobertura.csv", """
                tabela;%s;natureza
                CLASSIFICACAO_TRIBUTARIA;%s;FICTICIO
                NCM;%s;FICTICIO
                ITEM_ANEXO;%s;FICTICIO
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));
        escrever("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s
                XXX000;AAA;Dispositivo ficticio;false;;;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA));
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

    @Test
    void deveListarTodasAsLinhasRecusadasDeTodosOsArquivosNumaMensagemSo() throws IOException {
        escrever("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s
                123;Descricao ficticia;%s
                00000001;Descricao ficticia;1900-99-01;;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA, LINHA_FICTICIA));
        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;%s
                TRIBUTO_XX;99,99;ABRANGENCIA-XX;%s
                CBS;noventa;ABRANGENCIA-XX;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA, LINHA_FICTICIA));

        CargaRecusada recusa = catchThrowableOfType(
                () -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"), CargaRecusada.class);

        assertThat(recusa).as("a carga inteira é recusada").isNotNull();
        assertThat(recusa.recusadas()).hasSize(4);
        assertThat(recusa.getMessage())
                .contains("4 problema(s) em 2 arquivo(s)")
                .contains("Nada foi gravado")
                .contains("registro-ncm.csv: Linha 3, coluna \"ncm\", valor \"123\"")
                .contains("registro-ncm.csv: Linha 4, coluna \"vigenciaInicio\", valor \"1900-99-01\"")
                .contains("aliquota-vigente.csv: Linha 2, coluna \"tributo\", valor \"TRIBUTO_XX\"")
                .contains("aliquota-vigente.csv: Linha 3, coluna \"percentual\", valor \"noventa\"");
    }

    @Test
    void naturezaAusenteDeveFalharACargaENuncaAssumirValor() throws IOException {
        escrever("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s
                00000001;Descricao ficticia;%s;
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA, VIGENCIA_FICTICIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("registro-ncm.csv: Linha 3, coluna \"natureza\", valor em branco");
    }

    @Test
    void colunaNaturezaFaltandoNoCabecalhoDeveAparecerUmaVezSo() throws IOException {
        escrever("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s
                00000001;Descricao ficticia;%s
                00000002;Descricao ficticia;%s
                """.formatted(CABECALHO_COMUM, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA, VIGENCIA_FICTICIA));

        CargaRecusada recusa = catchThrowableOfType(
                () -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"), CargaRecusada.class);

        assertThat(recusa.recusadas())
                .as("falta de coluna é problema do arquivo, e não de cada uma das três linhas")
                .hasSize(1);
        assertThat(recusa.getMessage()).contains("não tem a coluna \"natureza\"");
    }

    @Test
    void deveRecusarCoberturaDeclaradaSobreTabelaSemRegistro() throws IOException {
        escrever("item-anexo.csv", "ncm;identificadorDoAnexo;tipoDeTratamento;%s%n".formatted(CABECALHO_DE_DADOS));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("cobertura sobre tabela sem nenhum registro")
                .hasMessageContaining("ITEM_ANEXO");
    }

    @Test
    void aTabelaDeAliquotasSemCoberturaContinuaPodendoVirVazia() throws IOException {
        escrever("aliquota-vigente.csv", "tributo;percentual;abrangencia;%s%n".formatted(CABECALHO_DE_DADOS));

        assertThat(LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia").aliquotas()).isEmpty();
    }

    @Test
    void osArquivosQueFaltamDevemSerListadosJuntos() throws IOException {
        Files.delete(diretorio.resolve("item-anexo.csv"));
        Files.delete(diretorio.resolve("cobertura.csv"));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("item-anexo.csv")
                .hasMessageContaining("cobertura.csv")
                .hasMessageContaining("2 problema(s) em 2 arquivo(s)");
    }

    @Test
    void aEdicaoPorCsvDeveTerAsMesmasRecusas() throws IOException {
        FontesDoCatalogo envio = FontesDoCatalogo.emMemoria(Map.of("registro-ncm.csv", """
                ncm;descricao;%s
                123;Descricao ficticia;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA).getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.lerSubstituicao(envio))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining("registro-ncm.csv: Linha 2, coluna \"ncm\", valor \"123\"");
    }

    @Test
    void aEdicaoSoSubstituiOsArquivosQueVieram() throws IOException {
        FontesDoCatalogo envio = FontesDoCatalogo.emMemoria(Map.of("registro-ncm.csv", """
                ncm;descricao;%s
                00000001;Outra descricao ficticia;%s
                """.formatted(CABECALHO_DE_DADOS, LINHA_FICTICIA).getBytes(StandardCharsets.UTF_8)));

        SubstituicaoDeTabelas substituicao = LeitorDeCatalogoEmCsv.lerSubstituicao(envio);

        assertThat(substituicao.registrosDeNcm()).isPresent();
        assertThat(substituicao.classificacoesTributarias()).isEqualTo(Optional.empty());
        assertThat(substituicao.cobertura()).isEmpty();
        assertThat(substituicao.tabelasSubstituidas()).containsExactly("NCM");
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
