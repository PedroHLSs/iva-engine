package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.SituacaoDaNatureza;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D021 (04/10/2026): o cobertura.csv declara natureza, como os outros arquivos de dados. A fonte que ele declara é citada como fundamento dos apontamentos, e fonte fictícia saía sob "Catálogo normativo" quando as outras tabelas eram normativas. Os dados são fictícios; NORMATIVO aqui só exercita a declaração, e não afirma nada sobre a lei.
class CoberturaNaNaturezaTest {

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";

    @TempDir
    private Path diretorio;

    @BeforeEach
    void escreverCatalogoNormativoMenosACobertura() throws IOException {
        escreverCobertura("FICTICIO");
        escrever("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX000;AAA|BBB;Dispositivo ficticio;false;;;%s;NORMATIVO
                """.formatted(VIGENCIA));
        escrever("registro-ncm.csv", """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;Descricao ficticia;%s;NORMATIVO
                """.formatted(VIGENCIA));
        escrever("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;NORMATIVO
                """.formatted(VIGENCIA));
        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CBS;99,99;ABRANGENCIA-XX;%s;NORMATIVO
                """.formatted(VIGENCIA));
    }

    // O caso demonstrado: tudo normativo e a cobertura fictícia não pode sair "Catálogo normativo".
    @Test
    void coberturaFicticiaDeveTornarACargaParcialmenteFicticia() throws IOException {
        NaturezaDaCarga natureza = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia").natureza();

        assertThat(natureza.cobertura()).contains(Natureza.FICTICIO);
        assertThat(natureza.situacao()).isEqualTo(SituacaoDaNatureza.PARCIALMENTE_FICTICIO);
        assertThat(natureza.tabelasFicticias()).containsExactly(NaturezaDaCarga.COBERTURA);
    }

    @Test
    void coberturaNormativaComORestoNormativoDeveSerNormativa() throws IOException {
        escreverCobertura("NORMATIVO");

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.natureza().situacao()).isEqualTo(SituacaoDaNatureza.NORMATIVO);
        assertThat(carga.natureza().declaradas()).containsKey(NaturezaDaCarga.COBERTURA);
    }

    // Sem a coluna, o cobertura.csv é recusado, como os outros arquivos de dados.
    @Test
    void coberturaSemNaturezaDeveSerRecusada() throws IOException {
        escrever("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa
                CLASSIFICACAO_TRIBUTARIA;%1$s
                NCM;%1$s
                ITEM_ANEXO;%1$s
                """.formatted(VIGENCIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("cobertura.csv")
                .hasMessageContaining("natureza");
    }

    @Test
    void coberturaComDuasNaturezasDeveSerRecusada() throws IOException {
        escrever("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;NORMATIVO
                ITEM_ANEXO;%1$s;NORMATIVO
                """.formatted(VIGENCIA));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"))
                .hasMessageContaining("cobertura.csv")
                .hasMessageContaining("procedência só");
    }

    // Carga gravada antes desta data: tudo normativo e a cobertura sem natureza não é "normativo".
    @Test
    void coberturaSemNaturezaNaCargaAntigaNaoDeixaSerNormativo() {
        NaturezaDaCarga antiga = new NaturezaDaCarga(Optional.of(Natureza.NORMATIVO), Optional.of(Natureza.NORMATIVO),
                Optional.of(Natureza.NORMATIVO), Optional.of(Natureza.NORMATIVO), Optional.of(Natureza.NORMATIVO));

        assertThat(antiga.situacao()).isEqualTo(SituacaoDaNatureza.NAO_DECLARADA);
        assertThat(antiga.tabelasSemNaturezaDeclarada()).containsExactly(NaturezaDaCarga.COBERTURA);
    }

    private void escreverCobertura(String natureza) throws IOException {
        escrever("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;%2$s
                NCM;%1$s;%2$s
                ITEM_ANEXO;%1$s;%2$s
                """.formatted(VIGENCIA, natureza));
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
