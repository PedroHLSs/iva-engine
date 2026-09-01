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

/**
 * Grava o relatório de acurácia em CSV.
 *
 * <p>Uma linha por regra, na ordem em que o conjunto as aplica, e uma linha
 * {@code CONSOLIDADO} ao final. O separador é {@code ;} e a codificação é UTF-8,
 * as mesmas convenções dos demais CSVs do sistema — de modo que o arquivo
 * produzido aqui pode ser relido pelo leitor deste próprio sistema.</p>
 *
 * <h2>O cabeçalho de comentário carrega a identificação da rodada</h2>
 *
 * <p>Versão do catálogo, versão do conjunto de regras, documentos, itens e as
 * contagens de alinhamento entre gabarito e acervo vão em linhas iniciadas por
 * {@code #}, que o leitor de CSV do sistema ignora e que qualquer pessoa lê. Não
 * viram colunas porque não variam por regra, e repeti-las em cada linha
 * convidaria a somá-las.</p>
 *
 * <p>Sem esse bloco, o arquivo diria "precisão 0,8571" sem dizer contra qual
 * catálogo — e um número de acurácia sem procedência não sustenta afirmação
 * nenhuma.</p>
 *
 * <h2>As três contagens que não são acerto nem erro</h2>
 *
 * <p>{@code nao_avaliados}, {@code sem_avaliacao} e {@code avaliados} são
 * colunas próprias, e {@code total} é a soma das três. Quem recontar as métricas
 * a partir deste arquivo chega aos mesmos números, e vê imediatamente que
 * nenhuma das duas primeiras entra em precisão ou recall.</p>
 */
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

    @Override
    public String extensao() {
        return EXTENSAO;
    }

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

    /**
     * Lista, em comentário, as linhas do gabarito que o motor não respondeu.
     *
     * <p>Não viram linhas de dados porque não são medição: são o desalinhamento
     * entre o gabarito e o acervo, e quem for corrigi-lo precisa dos endereços,
     * não de uma contagem.</p>
     */
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

    private static void comentario(BufferedWriter saida, String formato, Object... valores)
            throws IOException {

        saida.write("# " + formato.formatted(valores));
        saida.newLine();
    }
}
