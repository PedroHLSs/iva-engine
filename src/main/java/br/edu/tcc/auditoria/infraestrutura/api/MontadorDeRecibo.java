package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ResultadoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Monta o comprovante de uma análise, recém-feita ou reaberta do histórico.
 *
 * <p>Os dois caminhos produzem o mesmo tipo, e devem: um comprovante que mudasse
 * de forma ao ser relido seria um comprovante inútil meses depois.</p>
 */
@Component
class MontadorDeRecibo {

    static final String CAMINHO_BASE = "/api/analises/";

    private final ConsultaDeExecucoes execucoes;
    private final ConsultaDoAcervoDaAnalise acervo;

    MontadorDeRecibo(ConsultaDeExecucoes execucoes, ConsultaDoAcervoDaAnalise acervo) {
        this.execucoes = execucoes;
        this.acervo = acervo;
    }

    /** O comprovante da análise que acabou de rodar, com o que está em memória. */
    ReciboDaAnalise de(ResultadoDaAnalise resultado) {
        return montar(
                resultado.auditoria().execucao(),
                resultado.arquivosIlegiveis());
    }

    /** O comprovante de uma análise gravada. */
    ReciboDaAnalise porId(UUID id) {
        ExecucaoAuditoria execucao = execucoes.porId(id)
                .orElseThrow(() -> new ExecucaoNaoEncontrada(id));
        return montar(execucao, acervo.arquivosIlegiveis(id));
    }

    private static ReciboDaAnalise montar(
            ExecucaoAuditoria execucao, List<ArquivoIlegivel> ilegiveis) {

        return new ReciboDaAnalise(
                execucao.id().toString(),
                CAMINHO_BASE + execucao.id(),
                execucao.dataHora(),
                execucao.versaoCatalogo(),
                execucao.versaoConjuntoRegras(),
                execucao.quantidadeDocumentos(),
                execucao.quantidadeItens(),
                ilegiveis.size(),
                ilegiveis.stream().map(ArquivoIlegivelExposto::de).toList(),
                comoFoiALeitura(execucao, ilegiveis.size()));
    }

    /**
     * A leitura contada por extenso, nos quatro casos possíveis.
     *
     * <p>Escrita sempre, inclusive quando não houve nenhuma falha: "nenhum
     * arquivo deixou de ser lido" é uma afirmação, e um campo em branco no lugar
     * dela seria indistinguível de campo que ninguém preencheu.</p>
     */
    private static String comoFoiALeitura(ExecucaoAuditoria execucao, int ilegiveis) {
        int documentos = execucao.quantidadeDocumentos();

        if (documentos == 0 && ilegiveis > 0) {
            return ("Nenhum documento pôde ser lido: os %d arquivo(s) enviados falharam. A análise "
                    + "foi registrada assim mesmo, para que a tentativa e os motivos de cada falha "
                    + "fiquem gravados.").formatted(ilegiveis);
        }
        if (documentos == 0) {
            return "Nenhum documento fiscal foi encontrado na origem, e nenhum arquivo falhou ao ser "
                    + "lido: não havia o que analisar.";
        }
        if (ilegiveis == 0) {
            return ("%d documento(s) lido(s), com %d item(ns) ao todo. Nenhum arquivo deixou de ser "
                    + "lido.").formatted(documentos, execucao.quantidadeItens());
        }
        return ("%d documento(s) lido(s), com %d item(ns) ao todo. %d arquivo(s) não puderam ser "
                + "lidos e aparecem listados à parte — eles não entram em contagem nenhuma de "
                + "conferência, porque não chegaram a ser documentos.")
                .formatted(documentos, execucao.quantidadeItens(), ilegiveis);
    }
}
