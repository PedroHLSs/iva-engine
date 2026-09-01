package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Caso de uso: emitir o papel de trabalho de uma execução.
 *
 * <p>O papel de trabalho é a saída que uma pessoa confere. Tudo o que o sistema
 * fez antes — ler XML, resolver catálogo por vigência, aplicar regra — só vira
 * auditoria quando alguém consegue olhar uma linha e decidir se ela procede.</p>
 *
 * <h2>Sempre de uma execução, nunca "do banco"</h2>
 *
 * <p>Exportar "os apontamentos atuais" produziria uma planilha sem data de
 * corte, impossível de reconciliar com outra emitida uma semana depois. O papel
 * de trabalho é sempre o de uma rodada identificada, e a rodada carrega consigo
 * a versão do catálogo e a do conjunto de regras que valiam nela.</p>
 */
public final class ServicoDeExportacao {

    private final ConsultaDeExecucoes execucoes;
    private final MontadorDePapelDeTrabalho montador;
    private final ExportadorDePapelDeTrabalho exportador;

    public ServicoDeExportacao(
            ConsultaDeExecucoes execucoes,
            MontadorDePapelDeTrabalho montador,
            ExportadorDePapelDeTrabalho exportador) {
        this.execucoes = exigir(execucoes, "a consulta de execuções");
        this.montador = exigir(montador, "o montador do papel de trabalho");
        this.exportador = exigir(exportador, "o exportador");
    }

    /** Emite o papel de trabalho da execução mais recente. */
    public PapelDeTrabalho exportarUltima(Path destino) {
        ExecucaoAuditoria execucao = execucoes.maisRecente().orElseThrow(() ->
                new PapelDeTrabalhoInvalido(
                        "Nenhuma auditoria rodou ainda, então não há papel de trabalho a emitir. "
                                + "Rode \"auditar\" antes."));
        return exportar(execucao, destino);
    }

    /** Emite o papel de trabalho da execução indicada. */
    public PapelDeTrabalho exportar(UUID execucaoId, Path destino) {
        if (execucaoId == null) {
            throw new PapelDeTrabalhoInvalido("Não foi informada qual execução exportar.");
        }
        ExecucaoAuditoria execucao = execucoes.porId(execucaoId).orElseThrow(() ->
                new PapelDeTrabalhoInvalido(
                        "Não há execução com o identificador %s.".formatted(execucaoId)));
        return exportar(execucao, destino);
    }

    /** Extensão de arquivo que o exportador configurado produz, sem o ponto. */
    public String extensao() {
        return exportador.extensao();
    }

    private PapelDeTrabalho exportar(ExecucaoAuditoria execucao, Path destino) {
        if (destino == null) {
            throw new PapelDeTrabalhoInvalido("Não foi informado onde gravar o papel de trabalho.");
        }
        PapelDeTrabalho papel = montador.montar(execucao);
        exportador.exportar(papel, destino);
        return papel;
    }

    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O serviço de exportação precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
