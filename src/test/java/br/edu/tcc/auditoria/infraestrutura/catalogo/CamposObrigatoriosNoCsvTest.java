package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// D015 (03/10/2026): a coluna camposObrigatoriosCondicionados lida pelo importador real, em três estados — coluna ausente é recusada, célula em branco é "não declarado", NENHUM declara que o código não exige campo. Valores fictícios: código XXX007, CST AAA, datas em 1900, nomes de campo do vocabulário do sistema.
class CamposObrigatoriosNoCsvTest {

    private static final String CABECALHO_SEM_COLUNA = "codigo;cstsCompativeis;dispositivoLegal;"
            + "indicadorDeBeneficio;percentualReducao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza";

    private static final String CABECALHO = CABECALHO_SEM_COLUNA + ";camposObrigatoriosCondicionados";

    private static final String LINHA = "XXX007;AAA;Dispositivo ficticio;false;0;1900-01-01;;"
            + "FONTE FICTICIA v0.0;FICTICIO";

    private final ImportadorClassificacaoTributariaCsv importador = new ImportadorClassificacaoTributariaCsv();

    @Test
    void deveRecusarArquivoSemAColuna() {
        Throwable recusa = catchThrowable(() -> importador.importar(
                ArquivoDeTeste.conteudo(CABECALHO_SEM_COLUNA + "\n" + LINHA + "\n")));

        assertThat(recusa).isNotNull().hasMessageContaining("camposObrigatoriosCondicionados");
    }

    @Test
    void deveDeixarNaoDeclaradoQuandoACelulaVemEmBranco() throws IOException {
        assertThat(importarCom("").camposObrigatoriosCondicionados())
                .describedAs("célula em branco é a fonte não ter dito nada, e não \"nenhum campo\"")
                .isEmpty();
    }

    @Test
    void deveDeixarNaoDeclaradoQuandoACelulaTemSoEspacos() throws IOException {
        assertThat(importarCom("   ").camposObrigatoriosCondicionados()).isEmpty();
    }

    @Test
    void deveLerNenhumComoListaVaziaDeclarada() throws IOException {
        assertThat(importarCom("NENHUM").camposObrigatoriosCondicionados())
                .describedAs("NENHUM é declaração, e não ausência dela")
                .isEqualTo(Optional.of(List.of()));
    }

    @Test
    void deveLerOsNomesSeparadosPorBarraNaOrdemDoArquivo() throws IOException {
        assertThat(importarCom("valorCbs|baseCalculoIbs").camposObrigatoriosCondicionados())
                .isEqualTo(Optional.of(List.of("valorCbs", "baseCalculoIbs")));
    }

    @Test
    void deveRecusarNenhumMisturadoComNome() {
        assertRecusa("NENHUM|valorCbs");
    }

    @Test
    void deveRecusarNomeVazioEntreBarras() {
        assertRecusa("valorCbs||baseCalculoIbs");
    }

    @Test
    void deveRecusarBarraSobrandoNoFim() {
        assertRecusa("valorCbs|");
    }

    @Test
    void deveRecusarNomeComEspacoEmVolta() {
        assertRecusa("valorCbs | baseCalculoIbs");
    }

    @Test
    void deveRecusarNomeRepetido() {
        assertRecusa("valorCbs|valorCbs");
    }

    // Método auxiliar que confere a recusa da linha, com a linha, a coluna e o valor como veio.
    private void assertRecusa(String valor) {
        CargaRecusada recusa = catchThrowableOfType(
                () -> importador.importar(ArquivoDeTeste.conteudo(CABECALHO + "\n" + LINHA + ";" + valor + "\n")),
                CargaRecusada.class);

        assertThat(recusa).describedAs("\"%s\" deveria ter sido recusado", valor).isNotNull();
        assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
            assertThat(recusada.linha()).contains(2);
            assertThat(recusada.coluna()).contains("camposObrigatoriosCondicionados");
            assertThat(recusada.valor()).contains(valor);
        });
    }

    // Método auxiliar que importa uma linha só, com o valor indicado na coluna.
    private ClassificacaoTributaria importarCom(String valor) throws IOException {
        List<ClassificacaoTributaria> lidas = importador.importar(
                ArquivoDeTeste.conteudo(CABECALHO + "\n" + LINHA + ";" + valor + "\n")).registros();
        assertThat(lidas).hasSize(1);
        return lidas.get(0);
    }
}
