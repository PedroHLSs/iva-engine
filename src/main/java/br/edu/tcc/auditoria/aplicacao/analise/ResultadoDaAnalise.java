package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O que uma análise produziu: a auditoria, e os arquivos que não deu para ler.
 *
 * <h2>Os ilegíveis andam ao lado, nunca dentro</h2>
 *
 * <p>{@code ResultadoDaAuditoria} conta avaliações, apontamentos e avaliações
 * não concluídas — todas sobre documentos que <em>foram lidos</em>. Um arquivo
 * ilegível não tem item, não tem regra aplicada e não tem desfecho: ele não cabe
 * em nenhuma dessas contagens, e enfiá-lo em uma delas seria transformar
 * ausência em resultado.</p>
 *
 * <p>Por isso ele fica num campo próprio, deste tipo, e não dentro daquele. A
 * separação é a mesma que {@code ResumoDaConferencia} faz: não existe soma
 * possível por descuido porque não existe o campo onde somar.</p>
 */
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

    /**
     * Se a análise não conseguiu ler documento nenhum, tendo recebido arquivos.
     *
     * <p>Não é erro, e não vira exceção: é um resultado, e o resultado precisa
     * aparecer como tal. O contrário — falhar a requisição — apagaria a
     * informação de que houve tentativa e de quantos arquivos ela envolveu.</p>
     */
    public boolean nadaPodeSerLido() {
        return quantidadeDeDocumentosLidos() == 0 && !arquivosIlegiveis.isEmpty();
    }
}
