package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.aplicacao.acuracia.EnderecoDaAvaliacao;
import br.edu.tcc.auditoria.aplicacao.acuracia.EscritorDeRelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.MetricasDaRegra;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Classe que grava o relatório de acurácia em CSV: uma linha por regra e uma linha CONSOLIDADO no fim. As linhas com # no começo dizem contra qual catálogo e quais regras a medição foi feita, e os não avaliados ficam em coluna própria, fora da precisão e do recall.
@Component
public class EscritorDeRelatorioDeAcuraciaCsv implements EscritorDeRelatorioDeAcuracia {

    static final String EXTENSAO = "csv";
    static final String SEPARADOR = ";";
    static final String LINHA_CONSOLIDADA = "CONSOLIDADO";

    static final String CABECALHO = String.join(SEPARADOR,
            "regra_id",
            "verdadeiros_positivos",
            "falsos_positivos",
            "falsos_negativos",
            "verdadeiros_negativos",
            "avaliados",
            "nao_avaliados",
            "sem_avaliacao",
            "total",
            "precisao",
            "recall",
            "f1",
            "cobertura");

    // Grava o relatório no arquivo de destino, criando a pasta se ela não existir.
    @Override
    public void escrever(RelatorioDeAcuracia relatorio, Path destino) {
        if (relatorio == null) {
            throw new IllegalArgumentException("Não há relatório de acurácia a gravar.");
        }
        if (destino == null) {
            throw new IllegalArgumentException("Não foi informado onde gravar o relatório de acurácia.");
        }
        try {
            Path pasta = destino.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            try (BufferedWriter saida = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
                escreverIdentificacao(saida, relatorio);
                saida.write(CABECALHO);
                saida.newLine();
                for (MetricasDaRegra metricas : relatorio.porRegra()) {
                    escreverLinha(saida, metricas.regraId(), metricas.contagem());
                }
                escreverLinha(saida, LINHA_CONSOLIDADA, relatorio.consolidado());
            }
        } catch (IOException falhaDeEscrita) {
            throw new UncheckedIOException(
                    "Falha ao gravar o relatório de acurácia em \"%s\".".formatted(destino),
                    falhaDeEscrita);
        }
    }

    // Retorna a extensão do arquivo gravado.
    @Override
    public String extensao() {
        return EXTENSAO;
    }

    // Método auxiliar que escreve, em linhas com #, as versões usadas e as contagens da rodada.
    private static void escreverIdentificacao(BufferedWriter saida, RelatorioDeAcuracia relatorio)
            throws IOException {

        comentario(saida, "Avaliação de acurácia do motor de regras contra gabarito rotulado à mão.");
        comentario(saida, "catálogo: %s", relatorio.versaoDoCatalogo());
        comentario(saida, "conjunto de regras: %s", relatorio.versaoDoConjuntoDeRegras());
        comentario(saida, "documentos auditados: %d", relatorio.documentosAuditados());
        comentario(saida, "itens auditados: %d", relatorio.itensAuditados());
        comentario(saida, "avaliações produzidas pelo motor: %d", relatorio.avaliacoesProduzidas());
        comentario(saida, "linhas de gabarito: %d", relatorio.linhasDoGabarito());
        comentario(saida, "avaliações sem linha no gabarito: %d",
                relatorio.avaliacoesSemLinhaNoGabarito());
        comentario(saida, "linhas de gabarito sem avaliação correspondente: %d",
                relatorio.gabaritoSemAvaliacao().size());
        comentario(saida,
                "nao_avaliados e sem_avaliacao ficam fora de precisao, recall e f1; aparecem em "
                        + "cobertura, que é avaliados / total.");
        escreverEnderecosSemAvaliacao(saida, relatorio.gabaritoSemAvaliacao());
    }

    // Método auxiliar que lista, em linhas com #, as linhas do gabarito que o motor não avaliou. Ficam como comentário porque não são medição, e quem for corrigir o gabarito precisa saber quais são.
    private static void escreverEnderecosSemAvaliacao(
            BufferedWriter saida, List<EnderecoDaAvaliacao> enderecos) throws IOException {

        if (enderecos.isEmpty()) {
            return;
        }
        comentario(saida, "endereços do gabarito que o motor não avaliou:");
        for (EnderecoDaAvaliacao endereco : enderecos) {
            comentario(saida, "  documento %s, item %d, regra %s",
                    endereco.chaveAcesso().valor(), endereco.numeroItem(), endereco.regraId());
        }
    }

    // Método auxiliar que escreve a linha de uma regra, ou do consolidado, com as contagens e as métricas.
    private static void escreverLinha(BufferedWriter saida, String rotulo, ContagemDeAcuracia contagem)
            throws IOException {

        saida.write(String.join(SEPARADOR,
                rotulo,
                String.valueOf(contagem.verdadeirosPositivos()),
                String.valueOf(contagem.falsosPositivos()),
                String.valueOf(contagem.falsosNegativos()),
                String.valueOf(contagem.verdadeirosNegativos()),
                String.valueOf(contagem.avaliados()),
                String.valueOf(contagem.naoAvaliados()),
                String.valueOf(contagem.semAvaliacao()),
                String.valueOf(contagem.total()),
                TextoDeMetrica.de(contagem.precisao()),
                TextoDeMetrica.de(contagem.recall()),
                TextoDeMetrica.de(contagem.f1()),
                TextoDeMetrica.de(contagem.cobertura())));
        saida.newLine();
    }

    // Método auxiliar que escreve uma linha começando com #.
    private static void comentario(BufferedWriter saida, String formato, Object... valores)
            throws IOException {

        saida.write("# " + formato.formatted(valores));
        saida.newLine();
    }
}
