package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

//Classe record que representa o resultado de uma análise, incluindo o resultado da auditoria e a lista de arquivos ilegíveis.
public record ResultadoDaAnalise(
        ResultadoDaAuditoria auditoria, List<ArquivoIlegivel> arquivosIlegiveis) {

    public ResultadoDaAnalise {
        if (auditoria == null) {
            throw new AnaliseInvalida("A análise precisa do resultado da auditoria.");
        }
        if (arquivosIlegiveis == null) {
            throw new AnaliseInvalida(
                    "A lista de arquivos ilegíveis deve ser vazia quando todos foram lidos, nunca nula.");
        }
        if (arquivosIlegiveis.stream().anyMatch(Objects::isNull)) {
            throw new AnaliseInvalida("A lista de arquivos ilegíveis não pode conter elemento nulo.");
        }
        arquivosIlegiveis = List.copyOf(arquivosIlegiveis);
    }

    public UUID id() {
        return auditoria.execucao().id();
    }

    public int quantidadeDeDocumentosLidos() {
        return auditoria.execucao().quantidadeDocumentos();
    }

    public int quantidadeDeItensLidos() {
        return auditoria.execucao().quantidadeItens();
    }

    public int quantidadeDeArquivosIlegiveis() {
        return arquivosIlegiveis.size();
    }

    // Indica se a análise não conseguiu ler nenhum documento, mas encontrou arquivos ilegíveis.
    public boolean nadaPodeSerLido() {
        return quantidadeDeDocumentosLidos() == 0 && !arquivosIlegiveis.isEmpty();
    }
}
