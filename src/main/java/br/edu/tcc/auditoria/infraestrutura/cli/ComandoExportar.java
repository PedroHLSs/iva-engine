package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MotivoAgrupado;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ServicoDeExportacao;
import br.edu.tcc.auditoria.dominio.Severidade;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Classe do comando exportar, que grava o papel de trabalho de uma execução em xlsx. Sem --execucao, exporta a mais recente.
@Component
class ComandoExportar implements Comando {

    static final String NOME = "exportar";

    private static final String OPCAO_ARQUIVO = "arquivo";
    private static final String OPCAO_EXECUCAO = "execucao";

    private final ServicoDeExportacao servico;
    private final Saida saida;

    // Construtor que recebe o serviço de exportação e a saída.
    ComandoExportar(ServicoDeExportacao servico, Saida saida) {
        this.servico = servico;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Emite o papel de trabalho de uma execução em planilha.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --arquivo=<caminho.%s> [--execucao=<id>]

                  --arquivo   onde gravar a planilha. Pastas que faltarem são criadas.
                  --execucao  identificador da rodada a exportar, como sai em "%s".
                              Se omitido, exporta a rodada mais recente.

                A planilha tem três abas: Resumo, com a identificação da execução e
                os totais; Achados, uma linha por apontamento; e Não avaliados, com
                o motivo de cada avaliação que não concluiu.

                Nenhum identificador em texto claro sai na planilha. O documento
                aparece pelo pseudônimo da chave de acesso — cujos dígitos carregam
                o CNPJ do emitente — mais modelo, série, número, data e UF, que
                localizam a nota sem identificar ninguém.
                """.formatted(NOME, servico.extensao(), ComandoAuditar.NOME);
    }

    // Exporta a execução pedida, ou a mais recente, para o arquivo informado.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_ARQUIVO, OPCAO_EXECUCAO));

        Path arquivo = argumentos.caminhoObrigatorio(OPCAO_ARQUIVO);
        Optional<UUID> execucao = argumentos.texto(OPCAO_EXECUCAO)
                .map(informado -> argumentos.identificadorObrigatorio(OPCAO_EXECUCAO));

        PapelDeTrabalho papel = execucao
                .map(id -> servico.exportar(id, arquivo))
                .orElseGet(() -> servico.exportarUltima(arquivo));

        imprimir(papel, arquivo);
    }

    // Método auxiliar que mostra onde a planilha foi gravada e um resumo do conteúdo.
    private void imprimir(PapelDeTrabalho papel, Path arquivo) {
        saida.linha("Papel de trabalho gravado em %s", arquivo.toAbsolutePath());
        saida.linha("  execução ........... %s", papel.execucao().id());
        saida.linha("  em ................. %s", papel.execucao().dataHora());
        saida.linha("  catálogo ........... %s", papel.execucao().versaoCatalogo());
        saida.linha("  conjunto de regras . %s", papel.execucao().versaoConjuntoRegras());
        saida.linhaEmBranco();

        saida.linha("Apontamentos: %d", papel.achados().size());
        for (Severidade severidade : papel.execucao().severidadesContadas()) {
            saida.linha("  %-12s %d", severidade, papel.execucao().achadosDe(severidade));
        }
        saida.linhaEmBranco();

        saida.linha("Avaliações não concluídas: %d, atingindo %d item(ns).",
                papel.quantidadeDeNaoAvaliados(), papel.itensNaoAvaliados());
        if (papel.motivosAgrupados().isEmpty()) {
            return;
        }
        saida.linha("Motivos mais frequentes:");
        papel.motivosAgrupados().stream().limit(3).forEach(this::imprimirMotivo);
    }

    // Método auxiliar que mostra um motivo de não avaliação e quantas vezes apareceu.
    private void imprimirMotivo(MotivoAgrupado agrupado) {
        saida.linha("  %s x%d: %s", agrupado.regraId(), agrupado.quantidade(), agrupado.motivo());
    }
}
