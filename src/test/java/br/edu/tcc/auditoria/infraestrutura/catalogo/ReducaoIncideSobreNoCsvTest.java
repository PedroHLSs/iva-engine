package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IncidenciaDaReducao;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// D017 (03/10/2026): a coluna opcional reducaoIncideSobre de classificacao-tributaria.csv, lida pelo importador real. Coluna ausente ou célula em branco é "não declarado"; ALIQUOTA e BASE são os únicos valores; qualquer outro recusa a linha. Valores fictícios: código XXX007, CST AAA, datas em 1900.
class ReducaoIncideSobreNoCsvTest {

    private static final String CABECALHO = "codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;"
            + "percentualReducao;camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

    private static final String LINHA = "XXX007;AAA;Dispositivo ficticio;true;60;NENHUM;1900-01-01;;"
            + "FONTE FICTICIA v0.0;FICTICIO";

    private final ImportadorClassificacaoTributariaCsv importador = new ImportadorClassificacaoTributariaCsv();

    @Test
    void arquivoSemAColunaDeveDeixarNaoDeclarado() throws IOException {
        assertThat(importar(CABECALHO + "\n" + LINHA + "\n").reducaoIncideSobre()).isEmpty();
    }

    @Test
    void celulaEmBrancoDeveDeixarNaoDeclarado() throws IOException {
        assertThat(importarCom("").reducaoIncideSobre()).isEmpty();
    }

    @Test
    void deveLerAliquotaEBase() throws IOException {
        assertThat(importarCom("ALIQUOTA").reducaoIncideSobre()).contains(IncidenciaDaReducao.ALIQUOTA);
        assertThat(importarCom("BASE").reducaoIncideSobre()).contains(IncidenciaDaReducao.BASE);
    }

    @Test
    void deveRecusarValorForaDosDoisComALinhaEAColuna() {
        for (String valor : List.of("base", "Aliquota", "BASE_DE_CALCULO", "S")) {
            CargaRecusada recusa = catchThrowableOfType(() -> importarCom(valor), CargaRecusada.class);

            assertThat(recusa).describedAs("\"%s\" deveria ter sido recusado", valor).isNotNull();
            assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
                assertThat(recusada.linha()).contains(2);
                assertThat(recusada.coluna()).contains("reducaoIncideSobre");
                assertThat(recusada.valor()).contains(valor);
            });
        }
    }

    private ClassificacaoTributaria importarCom(String valor) throws IOException {
        return importar(CABECALHO + ";reducaoIncideSobre\n" + LINHA + ";" + valor + "\n");
    }

    private ClassificacaoTributaria importar(String csv) throws IOException {
        List<ClassificacaoTributaria> lidas = importador.importar(ArquivoDeTeste.conteudo(csv)).registros();
        assertThat(lidas).hasSize(1);
        return lidas.get(0);
    }
}
