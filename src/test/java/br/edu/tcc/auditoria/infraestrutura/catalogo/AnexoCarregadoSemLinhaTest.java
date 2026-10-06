package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// Guarda de 03/10/2026: anexo declarado carregado, de NCM ou de NCM e NBS, precisa de ao menos uma linha em item-anexo.csv vigente no período de carregamento. É o cenário da revisão adversarial: o anexo que o código admite declarado carregado, sem nenhuma linha, e a tabela de itens de anexo não vazia por causa de outro anexo — a guarda de tabela vazia passava, e a R03 apontava todo NCM. Valores fictícios: código XXX000, anexos ANEXO-XX e ANEXO-WW, NCM 00000000, datas em 1900.
class AnexoCarregadoSemLinhaTest {

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String CABECALHO_DOS_ANEXOS =
            "identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

    @TempDir
    private Path diretorio;

    // O cenário demonstrado: ANEXO-XX admitido pelo código e declarado carregado, sem nenhuma linha; ANEXO-WW tem linha.
    @Test
    void deveRecusarAnexoDeNcmDeclaradoCarregadoSemNenhumaLinha() throws IOException {
        escreverCatalogo("00000000;ANEXO-WW;TRATAMENTO-WW;" + VIGENCIA + ";FICTICIO\n");
        escreverAnexos("ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-WW;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        Throwable recusa = catchThrowable(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"));

        assertThat(recusa).isNotNull()
                .hasMessageContaining("ANEXO-XX")
                .hasMessageContaining("anexos-declarados.csv")
                .hasMessageContaining("item-anexo.csv")
                .hasMessageContaining("nenhuma linha");
        assertThat(recusa.getMessage()).doesNotContain("ANEXO-WW");
    }

    @Test
    void deveRecusarAnexoDeNcmENbsDeclaradoCarregadoSemNenhumaLinha() throws IOException {
        escreverCatalogo("00000000;ANEXO-WW;TRATAMENTO-WW;" + VIGENCIA + ";FICTICIO\n");
        escreverAnexos("ANEXO-XX;NCM_E_NBS;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-WW;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        assertThat(catchThrowable(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia")))
                .isNotNull().hasMessageContaining("ANEXO-XX");
    }

    // A linha existe, mas só vale antes do período em que o anexo foi declarado carregado: na data das notas o anexo estaria vazio do mesmo jeito.
    @Test
    void deveRecusarAnexoCujasLinhasNaoValemNoPeriodoDeCarregamento() throws IOException {
        escreverCatalogo("00000000;ANEXO-XX;TRATAMENTO-XX;1900-01-01;1900-03-31;FONTE FICTICIA v0.0;FICTICIO\n");
        escreverAnexos("ANEXO-XX;NCM;1900-07-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        assertThat(catchThrowable(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia")))
                .isNotNull().hasMessageContaining("ANEXO-XX").hasMessageContaining("1900-07-01");
    }

    @Test
    void deveNomearTodosOsAnexosSemLinhaNumaRecusaSo() throws IOException {
        escreverCatalogo("00000000;ANEXO-WW;TRATAMENTO-WW;" + VIGENCIA + ";FICTICIO\n");
        escreverAnexos("ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-YY;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-WW;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        assertThat(catchThrowable(() -> LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia")))
                .isNotNull().hasMessageContaining("ANEXO-XX").hasMessageContaining("ANEXO-YY");
    }

    @Test
    void deveAceitarAnexoDeNcmCarregadoComLinhaNoPeriodo() throws IOException {
        escreverCatalogo("00000000;ANEXO-XX;TRATAMENTO-XX;" + VIGENCIA + ";FICTICIO\n");
        escreverAnexos("ANEXO-XX;NCM;1900-06-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        CargaDeCatalogo carga = LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");

        assertThat(carga.cobertura().anexosDeclarados()).hasSize(1);
    }

    // D7: anexo de NBS carregado e sem linha está "carregado e vazio de NCM" — item-anexo.csv só guarda NCM.
    @Test
    void deveAceitarAnexoDeNbsCarregadoSemNenhumaLinha() throws IOException {
        escreverCatalogo("00000000;ANEXO-WW;TRATAMENTO-WW;" + VIGENCIA + ";FICTICIO\n");
        escreverAnexos("ANEXO-XX;NBS;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-WW;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        assertThat(LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia").cobertura().anexosDeclarados())
                .hasSize(2);
    }

    // D6 e D9: anexo declarado sem vigência existe só para validar identificador; não está carregado, e a R03 não conclui sobre ele.
    @Test
    void deveAceitarAnexoDeNcmNaoCarregadoSemNenhumaLinha() throws IOException {
        escreverCatalogo("00000000;ANEXO-WW;TRATAMENTO-WW;" + VIGENCIA + ";FICTICIO\n");
        escreverAnexos("ANEXO-XX;NCM;;;FONTE FICTICIA v0.0;FICTICIO\n"
                + "ANEXO-WW;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO\n");

        assertThat(LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia").cobertura().anexosDeclarados())
                .hasSize(2);
    }

    // Método auxiliar que escreve as cinco tabelas obrigatórias; o código admite ANEXO-XX, e item-anexo.csv recebe as linhas indicadas.
    private void escreverCatalogo(String linhasDeItemAnexo) throws IOException {
        escrever("cobertura.csv", "tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza\n"
                + "CLASSIFICACAO_TRIBUTARIA;" + VIGENCIA + ";FICTICIO\nNCM;" + VIGENCIA
                + ";FICTICIO\nITEM_ANEXO;" + VIGENCIA + ";FICTICIO\n");
        escrever("classificacao-tributaria.csv", "codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;"
                + "percentualReducao;camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;"
                + "natureza;anexosAdmitidos\n"
                + "XXX000;AAA;Dispositivo ficticio;true;0;NENHUM;" + VIGENCIA + ";FICTICIO;ANEXO-XX\n");
        escrever("registro-ncm.csv", "ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza\n"
                + "00000000;Descricao ficticia;" + VIGENCIA + ";FICTICIO\n");
        escrever("item-anexo.csv", "ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;"
                + "fonteNormativa;natureza\n" + linhasDeItemAnexo);
        escrever("aliquota-vigente.csv", "tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;"
                + "fonteNormativa;natureza\nCBS;99,99;ABRANGENCIA-XX;" + VIGENCIA + ";FICTICIO\n");
    }

    private void escreverAnexos(String linhas) throws IOException {
        escrever("anexos-declarados.csv", CABECALHO_DOS_ANEXOS + "\n" + linhas);
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
