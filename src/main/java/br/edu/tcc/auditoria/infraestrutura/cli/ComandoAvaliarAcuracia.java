package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.acuracia.AvaliacaoDeAcuraciaInvalida;
import br.edu.tcc.auditoria.aplicacao.acuracia.EnderecoDaAvaliacao;
import br.edu.tcc.auditoria.aplicacao.acuracia.MetricasDaRegra;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.ServicoDeAvaliacaoDeAcuracia;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
import br.edu.tcc.auditoria.infraestrutura.acuracia.GabaritoInvalido;
import br.edu.tcc.auditoria.infraestrutura.acuracia.LeitorDeGabaritoCsv;
import br.edu.tcc.auditoria.infraestrutura.acuracia.TextoDeMetrica;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// Classe do comando avaliar-acuracia, que roda o motor sobre a origem, compara com o gabarito e mostra precisão, recall e F1 por regra e no total. As colunas NaoAval e SemAval ficam separadas, fora das métricas, e nada é gravado no banco: medir não é auditar.
@Component
class ComandoAvaliarAcuracia implements Comando {

    static final String NOME = "avaliar-acuracia";

    private static final String OPCAO_ORIGEM = "origem";
    private static final String OPCAO_GABARITO = "gabarito";
    private static final String OPCAO_RELATORIO = "relatorio";

    private static final String FORMATO_DE_LINHA =
            "  %-12s %4s %4s %4s %4s %9s %9s %9s %6s  |  %12s %12s %12s";
    private static final int LARGURA_DA_TABELA = 112;
    private static final int LIMITE_DE_ENDERECOS_IMPRESSOS = 10;

    private final ServicoDeAvaliacaoDeAcuracia servico;
    private final Saida saida;

    // Construtor que recebe o serviço de avaliação de acurácia e a saída.
    ComandoAvaliarAcuracia(ServicoDeAvaliacaoDeAcuracia servico, Saida saida) {
        this.servico = servico;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Mede precisão, recall e F1 do motor contra um gabarito rotulado.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --origem=<caminho> --gabarito=<caminho.csv> [--relatorio=<caminho.%s>]

                  --origem     diretório com arquivos .xml, ou um arquivo .zip que os
                               contenha. Subdiretórios são percorridos.
                  --gabarito   CSV rotulado à mão, com as colunas, nesta ordem ou em
                               qualquer outra: %s
                  --relatorio  onde gravar o resultado. Pastas que faltarem são
                               criadas. Se omitido, o resultado sai apenas no terminal.

                O gabarito tem uma linha por item e regra que se queira medir, e
                rotulo_esperado é ACHADO ou CONFORME — nada mais. Separador ";",
                codificação UTF-8, linhas iniciadas por "#" são comentário.

                NAO_AVALIADO não é rótulo de gabarito e não conta como acerto nem
                como erro: quando o motor não consegue julgar, isso aparece na
                cobertura, que é avaliados / total, e nunca em precisão ou recall.

                Nada é gravado no banco: medir não é auditar.
                """.formatted(NOME, servico.extensao(), LeitorDeGabaritoCsv.colunasEsperadas());
    }

    // Mede a acurácia com a origem e o gabarito informados e, se houver --relatorio, grava o resultado em arquivo.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_ORIGEM, OPCAO_GABARITO, OPCAO_RELATORIO));

        Path origem = argumentos.caminhoObrigatorio(OPCAO_ORIGEM);
        Path gabarito = argumentos.caminhoObrigatorio(OPCAO_GABARITO);
        Optional<Path> relatorio = argumentos.caminho(OPCAO_RELATORIO);

        try {
            RelatorioDeAcuracia medido = relatorio
                    .map(destino -> servico.avaliarEGravar(origem, gabarito, destino))
                    .orElseGet(() -> servico.avaliar(origem, gabarito));
            imprimir(medido, relatorio);

        } catch (GabaritoInvalido | AvaliacaoDeAcuraciaInvalida recusa) {
            // Gabarito malformado é erro de quem chamou; vira erro de uso para a mensagem sair junto com o formato esperado.
            throw new UsoInvalido(recusa.getMessage());
        }
    }

    // Método auxiliar que mostra a identificação da rodada, a tabela e as notas.
    private void imprimir(RelatorioDeAcuracia relatorio, Optional<Path> destino) {
        saida.linha("Avaliação de acurácia");
        saida.linha("  catálogo ........... %s", relatorio.versaoDoCatalogo());
        saida.linha("  conjunto de regras . %s", relatorio.versaoDoConjuntoDeRegras());
        saida.linha("  documentos ......... %d", relatorio.documentosAuditados());
        saida.linha("  itens .............. %d", relatorio.itensAuditados());
        saida.linha("  avaliações ......... %d", relatorio.avaliacoesProduzidas());
        saida.linha("  linhas de gabarito . %d", relatorio.linhasDoGabarito());
        saida.linhaEmBranco();

        imprimirTabela(relatorio);
        imprimirNotas(relatorio);
        destino.ifPresent(caminho ->
                saida.linha("Relatório gravado em %s", caminho.toAbsolutePath()));
    }

    // Método auxiliar que mostra a tabela de contagens e métricas, uma linha por regra e o consolidado.
    private void imprimirTabela(RelatorioDeAcuracia relatorio) {
        saida.linha(FORMATO_DE_LINHA,
                "Regra", "VP", "FP", "FN", "VN", "Avaliados", "NaoAval", "SemAval", "Total",
                "Precisão", "Recall", "F1");
        saida.linha("  " + "-".repeat(LARGURA_DA_TABELA));

        for (MetricasDaRegra metricas : relatorio.porRegra()) {
            imprimirContagem(metricas.regraId(), metricas.contagem());
        }
        saida.linha("  " + "-".repeat(LARGURA_DA_TABELA));
        imprimirContagem("CONSOLIDADO", relatorio.consolidado());
        saida.linhaEmBranco();

        imprimirCobertura(relatorio);
    }

    // Método auxiliar que mostra a linha de uma regra, ou do consolidado.
    private void imprimirContagem(String rotulo, ContagemDeAcuracia contagem) {
        saida.linha(FORMATO_DE_LINHA,
                rotulo,
                contagem.verdadeirosPositivos(),
                contagem.falsosPositivos(),
                contagem.falsosNegativos(),
                contagem.verdadeirosNegativos(),
                contagem.avaliados(),
                contagem.naoAvaliados(),
                contagem.semAvaliacao(),
                contagem.total(),
                TextoDeMetrica.de(contagem.precisao()),
                TextoDeMetrica.de(contagem.recall()),
                TextoDeMetrica.de(contagem.f1()));
    }

    // Método auxiliar que mostra a cobertura num bloco próprio, porque ela diz sobre quanto o motor se pronunciou, e não quanto ele acerta.
    private void imprimirCobertura(RelatorioDeAcuracia relatorio) {
        saida.linha("Cobertura (avaliados / total do gabarito)");
        for (MetricasDaRegra metricas : relatorio.porRegra()) {
            imprimirCoberturaDe(metricas.regraId(), metricas.contagem());
        }
        imprimirCoberturaDe("CONSOLIDADO", relatorio.consolidado());
        saida.linhaEmBranco();
    }

    // Método auxiliar que mostra a cobertura de uma regra, ou do consolidado.
    private void imprimirCoberturaDe(String rotulo, ContagemDeAcuracia contagem) {
        saida.linha("  %-12s %-12s  (%d de %d)",
                rotulo, TextoDeMetrica.de(contagem.cobertura()), contagem.avaliados(), contagem.total());
    }

    // Método auxiliar que explica o que não entra nas métricas e quantas avaliações ficaram fora da medição.
    private void imprimirNotas(RelatorioDeAcuracia relatorio) {
        ContagemDeAcuracia consolidado = relatorio.consolidado();

        saida.linha("NaoAval e SemAval não entram em precisão, recall nem F1.");
        saida.linha("  NaoAval:  o motor não pôde julgar — faltou campo no documento ou tabela no");
        saida.linha("            catálogo. Foram %d.", consolidado.naoAvaliados());
        saida.linha("  SemAval:  o gabarito aponta item que o motor não avaliou. Foram %d.",
                consolidado.semAvaliacao());
        saida.linha("Métrica sem denominador sai como %s, e não como zero ou um.",
                TextoDeMetrica.INDEFINIDA);
        saida.linha("%d avaliação(ões) do motor não têm linha no gabarito e ficaram fora da medição.",
                relatorio.avaliacoesSemLinhaNoGabarito());
        saida.linhaEmBranco();

        imprimirEnderecosSemAvaliacao(relatorio.gabaritoSemAvaliacao());
    }

    // Método auxiliar que mostra até dez linhas do gabarito que o motor não avaliou.
    private void imprimirEnderecosSemAvaliacao(List<EnderecoDaAvaliacao> enderecos) {
        if (enderecos.isEmpty()) {
            return;
        }
        saida.linha("Linhas do gabarito que o motor não avaliou:");
        enderecos.stream().limit(LIMITE_DE_ENDERECOS_IMPRESSOS).forEach(endereco ->
                saida.linha("  documento %s, item %d, regra %s",
                        endereco.chaveAcesso().valor(), endereco.numeroItem(), endereco.regraId()));
        if (enderecos.size() > LIMITE_DE_ENDERECOS_IMPRESSOS) {
            saida.linha("  ... e mais %d. A lista completa vai no relatório em arquivo.",
                    enderecos.size() - LIMITE_DE_ENDERECOS_IMPRESSOS);
        }
        saida.linhaEmBranco();
    }
}
