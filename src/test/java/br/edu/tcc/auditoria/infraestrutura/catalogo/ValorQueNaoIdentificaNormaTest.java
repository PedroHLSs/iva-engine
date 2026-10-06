package br.edu.tcc.auditoria.infraestrutura.catalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D022 (04/10/2026): dispositivo legal e fonte normativa que não têm letra nenhuma — "0", "-" — não identificam norma alguma, e a linha é recusada. Antes, "0" no lado do IBS entrava, e a tela mostrava um fundamento que não existe. O critério é de forma, não de conteúdo: o sistema não sabe qual norma é a certa (CLAUDE.md, seção 5), só que um número solto não é norma. Todos os valores são fictícios.
class ValorQueNaoIdentificaNormaTest {

    private static final String VIGENCIA = "1900-01-01;1900-12-31";
    private static final String CABECALHO_POR_TRIBUTO = "codigo;cstsCompativeis;dispositivoLegal_cbs;dispositivoLegal_ibs;"
            + "indicadorDeBeneficio;percentualReducao;camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;"
            + "fonteNormativa_cbs;fonteNormativa_ibs;natureza";

    @TempDir
    private Path diretorio;

    @BeforeEach
    void escreverCatalogoValido() throws IOException {
        escrever("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FONTE FICTICIA v0.0;FICTICIO
                NCM;%1$s;FONTE FICTICIA v0.0;FICTICIO
                ITEM_ANEXO;%1$s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        classificacao("Dispositivo ficticio A", "Dispositivo ficticio B", "FONTE FICTICIA A", "FONTE FICTICIA B");
        escrever("registro-ncm.csv", """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;Descricao ficticia;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CBS;99,99;ABRANGENCIA-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
    }

    // Controle: o catálogo com texto em todos os campos importa.
    @Test
    void catalogoComDispositivoEFonteDeTextoDeveImportar() {
        assertThatCode(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia")).doesNotThrowAnyException();
    }

    // O caso do código 200025: "0" no dispositivo do IBS.
    @Test
    void zeroNoDispositivoDoIbsDeveSerRecusado() throws IOException {
        classificacao("Dispositivo ficticio A", "0", "FONTE FICTICIA A", "FONTE FICTICIA B");

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("dispositivoLegal_ibs")
                .hasMessageContaining("não identifica norma");
    }

    // O mesmo caso, na fonte do IBS.
    @Test
    void zeroNaFonteDoIbsDeveSerRecusado() throws IOException {
        classificacao("Dispositivo ficticio A", "Dispositivo ficticio B", "FONTE FICTICIA A", "0");

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("fonteNormativa_ibs")
                .hasMessageContaining("não identifica norma");
    }

    @Test
    void zeroNaColunaUnicaDeDispositivoDeveSerRecusado() throws IOException {
        escrever("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX000;AAA;0;false;;NENHUM;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("dispositivoLegal")
                .hasMessageContaining("não identifica norma");
    }

    // A fonte de qualquer arquivo do catálogo, e não só da classificação.
    @Test
    void fonteSemLetraEmOutroArquivoDeveSerRecusada() throws IOException {
        escrever("registro-ncm.csv", """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;Descricao ficticia;%s;-;FICTICIO
                """.formatted(VIGENCIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("registro-ncm.csv")
                .hasMessageContaining("fonteNormativa")
                .hasMessageContaining("não identifica norma");
    }

    @Test
    void fonteSemLetraNaCoberturaDeveSerRecusada() throws IOException {
        escrever("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;0;FICTICIO
                NCM;%1$s;FONTE FICTICIA v0.0;FICTICIO
                ITEM_ANEXO;%1$s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("cobertura.csv")
                .hasMessageContaining("não identifica norma");
    }

    @Test
    void fonteSemLetraNosAnexosDeclaradosDeveSerRecusada() throws IOException {
        escrever("anexos-declarados.csv", """
                identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                ANEXO-XX;NCM;1900-01-01;;0;FICTICIO
                """);

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("anexos-declarados.csv")
                .hasMessageContaining("não identifica norma");
    }

    private void classificacao(String dispositivoCbs, String dispositivoIbs, String fonteCbs, String fonteIbs)
            throws IOException {
        escrever("classificacao-tributaria.csv", CABECALHO_POR_TRIBUTO + "\n"
                + "XXX000;AAA;%s;%s;false;;NENHUM;%s;%s;%s;FICTICIO\n".formatted(
                        dispositivoCbs, dispositivoIbs, VIGENCIA, fonteCbs, fonteIbs));
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }

    // Autoverificação: a mensagem da recusa não pode depender de outro problema do arquivo.
    @Test
    void aRecusaDeveSerPeloValorEPorNadaMais() throws IOException {
        classificacao("Dispositivo ficticio A", "0", "FONTE FICTICIA A", "FONTE FICTICIA B");

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .isInstanceOf(CargaRecusada.class)
                .satisfies(recusa -> assertThat(((CargaRecusada) recusa).recusadas()).hasSize(1));
    }
}
