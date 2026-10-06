package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.aplicacao.acuracia.MetricasDaRegra;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.auditoria.OrigemDaTolerancia;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// D023 (04/10/2026): o relatório de acurácia diz com que tolerância a R05 foi medida — as métricas da R05 dependem dela — e, sem o registro, diz que não foi registrada. Valores fictícios.
class ToleranciaNoRelatorioDeAcuraciaTest {

    @TempDir
    Path pasta;

    @Test
    void oCsvDeveDizerAToleranciaComAOrigem() throws IOException {
        RelatorioDeAcuracia relatorio = relatorio().comTolerancia(new ToleranciaDaExecucao(
                ToleranciaDeValor.de(new BigDecimal("0.07")), OrigemDaTolerancia.PADRAO));

        assertThat(escrever(relatorio))
                .contains("# tolerância de valor (R05): 0.07 (padrão do sistema; a instalação não configurou outra)");
    }

    @Test
    void semToleranciaOCsvDeveDizerQueNaoFoiRegistrada() throws IOException {
        assertThat(escrever(relatorio())).contains("# tolerância de valor (R05): não registrada");
    }

    private String escrever(RelatorioDeAcuracia relatorio) throws IOException {
        Path destino = pasta.resolve("acuracia-" + System.nanoTime() + ".csv");
        new EscritorDeRelatorioDeAcuraciaCsv().escrever(relatorio, destino);
        return Files.readString(destino, StandardCharsets.UTF_8);
    }

    private static RelatorioDeAcuracia relatorio() {
        return new RelatorioDeAcuracia("catalogo-ficticio-0", "conjunto-ficticio-0", 1, 1, 1, 0,
                List.of(new MetricasDaRegra("RX1", ContagemDeAcuracia.nenhuma())), List.of());
    }
}
