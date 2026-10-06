package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A coluna {@code anexosAdmitidos} do CSV de classificação, lida pelo importador
 * real (decisão do usuário de 30/09/2026, D2).
 *
 * <p>Coluna ausente ou célula em branco é "não declarado"; {@code NENHUM} é o
 * conjunto vazio; identificadores vêm separados por {@code |}. Valor malformado
 * recusa a carga inteira, como na Etapa 12.</p>
 *
 * <p>Todos os valores são fictícios: código {@code XXX009}, CST {@code AAA},
 * anexos {@code ANEXO-XX} e {@code ANEXO-YY}, datas em 1900. Nenhum deles é
 * afirmação sobre a legislação.</p>
 */
class AnexosAdmitidosNoCsvTest {

    private static final String CABECALHO = "codigo;cstsCompativeis;"
            + "dispositivoLegal_cbs;dispositivoLegal_ibs;indicadorDeBeneficio;reducao_cbs;reducao_ibs;"
            + "camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;"
            + "fonteNormativa_cbs;fonteNormativa_ibs;natureza";

    private static final String CABECALHO_COM_COLUNA = CABECALHO + ";anexosAdmitidos";

    private static final String CODIGO = "XXX009";

    private static final String LINHA_SEM_COLUNA = CODIGO + ";AAA;Dispositivo ficticio;Dispositivo ficticio;"
            + "true;0;0;;1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO";

    private final ImportadorClassificacaoTributariaCsv importador = new ImportadorClassificacaoTributariaCsv();

    @Test
    void deveDeixarNaoDeclaradoQuandoOArquivoNaoTemAColuna() throws IOException {
        ClassificacaoTributaria lida = importarUma(CABECALHO, LINHA_SEM_COLUNA);

        assertThat(lida.anexosAdmitidos()).isEmpty();
    }

    @Test
    void deveDeixarNaoDeclaradoQuandoACelulaVemEmBranco() throws IOException {
        ClassificacaoTributaria lida = importarUma(CABECALHO_COM_COLUNA, LINHA_SEM_COLUNA + ";");

        assertThat(lida.anexosAdmitidos()).isEmpty();
    }

    @Test
    void deveLerNenhumComoConjuntoVazio() throws IOException {
        ClassificacaoTributaria lida = importarUma(CABECALHO_COM_COLUNA, LINHA_SEM_COLUNA + ";NENHUM");

        assertThat(lida.anexosAdmitidos())
                .describedAs("NENHUM é declaração, e não ausência dela")
                .contains(Set.of());
    }

    @Test
    void deveLerOsIdentificadoresSeparadosPorBarra() throws IOException {
        ClassificacaoTributaria lida =
                importarUma(CABECALHO_COM_COLUNA, LINHA_SEM_COLUNA + ";ANEXO-XX|ANEXO-YY");

        assertThat(lida.anexosAdmitidos()).isEqualTo(Optional.of(
                Set.of(new IdentificadorAnexo("ANEXO-XX"), new IdentificadorAnexo("ANEXO-YY"))));
    }

    @Test
    void deveRecusarNenhumMisturadoComAnexo() {
        assertRecusa("NENHUM|ANEXO-XX");
    }

    @Test
    void deveRecusarIdentificadorVazioEntreBarras() {
        assertRecusa("ANEXO-XX||ANEXO-YY");
    }

    @Test
    void deveRecusarIdentificadorRepetido() {
        assertRecusa("ANEXO-XX|ANEXO-XX");
    }

    @Test
    void deveRecusarACargaInteiraMesmoComUmaLinhaBoa() {
        String csv = CABECALHO_COM_COLUNA + "\n"
                + LINHA_SEM_COLUNA + ";ANEXO-XX\n"
                + LINHA_SEM_COLUNA.replace(CODIGO, "XXX008") + ";ANEXO-XX||\n";

        CargaRecusada recusa = catchThrowableOfType(
                () -> importador.importar(ArquivoDeTeste.conteudo(csv)), CargaRecusada.class);

        assertThat(recusa).describedAs("tudo ou nada, como na Etapa 12").isNotNull();
        assertThat(recusa.recusadas()).singleElement()
                .satisfies(recusada -> assertThat(recusada.linha()).contains(3));
    }

    private void assertRecusa(String valor) {
        CargaRecusada recusa = catchThrowableOfType(
                () -> importador.importar(ArquivoDeTeste.conteudo(
                        CABECALHO_COM_COLUNA + "\n" + LINHA_SEM_COLUNA + ";" + valor + "\n")),
                CargaRecusada.class);

        assertThat(recusa).describedAs("\"%s\" deveria ter sido recusado", valor).isNotNull();
        assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
            assertThat(recusada.linha()).contains(2);
            assertThat(recusada.coluna()).contains("anexosAdmitidos");
            assertThat(recusada.valor()).contains(valor);
        });
    }

    private ClassificacaoTributaria importarUma(String cabecalho, String linha) throws IOException {
        List<ClassificacaoTributaria> lidas =
                importador.importar(ArquivoDeTeste.conteudo(cabecalho + "\n" + linha + "\n")).registros();
        assertThat(lidas).hasSize(1);
        return lidas.get(0);
    }
}
