package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.AchadoLocalizado;
import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.RepositorioDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grava uma rodada de auditoria inteira: execução, documentos, itens e
 * apontamentos.
 *
 * <h2>Reprocessar o mesmo lote não duplica nada</h2>
 *
 * <p>Cada coisa é gravada pela sua identidade natural, e não por linha nova:</p>
 *
 * <ul>
 *   <li>documento, pela chave de acesso;</li>
 *   <li>item, pela chave de acesso e pelo número do item;</li>
 *   <li>apontamento, pelo resumo do item, pelo identificador da regra e pela
 *       versão da regra — a mesma chave da tratativa.</li>
 * </ul>
 *
 * <p>As avaliações que não concluíram são a exceção: elas <strong>não</strong>
 * são deduplicadas, porque não concluir é fato da rodada e não do documento. A
 * mesma regra sobre o mesmo item pode não concluir hoje por falta de tabela no
 * catálogo e concluir amanhã, e o papel de trabalho de cada execução precisa
 * mostrar o que valia na hora dela.</p>
 *
 * <p>Só a execução é sempre nova: cada rodada é um fato distinto, com sua hora e
 * suas contagens. O apontamento reencontrado tem o conteúdo atualizado e passa a
 * apontar para a execução que acabou de vê-lo, mantendo a primeira detecção. É
 * isso que faz a tratativa dada por uma pessoa continuar valendo depois de o lote
 * rodar de novo: a linha do apontamento é a mesma, e a chave que a tratativa usa
 * não mudou.</p>
 *
 * <h2>Ordem de gravação</h2>
 *
 * <p>Execução, depois documentos e itens, depois apontamentos. O apontamento tem
 * chave estrangeira para o item e para a execução; gravar fora dessa ordem
 * falharia na primeira restrição.</p>
 */
@Repository
class RepositorioDaAuditoriaNoBanco implements RepositorioDaAuditoria {

    private final ExecucaoAuditoriaJpa execucoes;
    private final DocumentoJpa documentos;
    private final ItemDocumentoJpa itens;
    private final AchadoJpa achados;
    private final AchadoDaExecucaoJpa achadosDaExecucao;
    private final AvaliacaoNaoConcluidaJpa naoConcluidas;

    RepositorioDaAuditoriaNoBanco(
            ExecucaoAuditoriaJpa execucoes,
            DocumentoJpa documentos,
            ItemDocumentoJpa itens,
            AchadoJpa achados,
            AchadoDaExecucaoJpa achadosDaExecucao,
            AvaliacaoNaoConcluidaJpa naoConcluidas) {
        this.execucoes = execucoes;
        this.documentos = documentos;
        this.itens = itens;
        this.achados = achados;
        this.achadosDaExecucao = achadosDaExecucao;
        this.naoConcluidas = naoConcluidas;
    }

    @Override
    @Transactional
    public void persistir(ResultadoDaAuditoria resultado) {
        ExecucaoAuditoria execucao = resultado.execucao();
        gravarExecucao(execucao);

        for (DocumentoComItens documento : resultado.documentos()) {
            gravarDocumento(documento, execucao.dataHora());
        }
        for (AchadoLocalizado localizado : resultado.achados()) {
            UUID achadoId = gravarAchado(localizado, execucao.id(), execucao.dataHora());
            achadosDaExecucao.save(new AchadoDaExecucaoEntidade(execucao.id(), achadoId));
        }
        gravarNaoConcluidas(resultado.naoAvaliadas(), execucao.id());
    }

    /**
     * Grava as avaliações que não concluíram desta execução.
     *
     * <p>Sem procurar linha existente: cada execução tem as suas, e a chave única
     * da tabela é por execução.</p>
     */
    private void gravarNaoConcluidas(List<Avaliacao.NaoAvaliada> avaliacoes, UUID execucaoId) {
        List<AvaliacaoNaoConcluidaEntidade> linhas = new ArrayList<>();
        for (Avaliacao.NaoAvaliada avaliacao : avaliacoes) {
            linhas.add(new AvaliacaoNaoConcluidaEntidade(
                    UUID.randomUUID(),
                    execucaoId,
                    avaliacao.chaveAcesso().valor(),
                    avaliacao.numeroItem().orElseThrow(() -> new PersistenciaInconsistente(
                            ("A regra %s não concluiu sobre o documento %s inteiro, sem item. A "
                                    + "gravação de avaliação de documento ainda não existe.")
                                    .formatted(avaliacao.regraId(),
                                            avaliacao.chaveAcesso().valor()))),
                    avaliacao.regraId(),
                    avaliacao.regraVersao(),
                    avaliacao.motivo()));
        }
        naoConcluidas.saveAll(linhas);
    }

    private void gravarExecucao(ExecucaoAuditoria execucao) {
        execucoes.save(new ExecucaoAuditoriaEntidade(
                execucao.id(),
                execucao.dataHora(),
                execucao.hashEntrada(),
                execucao.versaoCatalogo(),
                execucao.versaoConjuntoRegras(),
                execucao.quantidadeDocumentos(),
                execucao.quantidadeItens(),
                execucao.achadosPorSeveridade(),
                execucao.achadosPorRegra()));
    }

    private void gravarDocumento(DocumentoComItens documentoComItens, Instant momento) {
        String chaveAcesso = documentoComItens.documento().chaveAcesso().valor();

        DocumentoEntidade documento = documentos.findById(chaveAcesso)
                .orElseGet(() -> new DocumentoEntidade(chaveAcesso));
        MapeadorDeDocumento.preencher(documento, documentoComItens.documento(), momento);
        documentos.save(documento);

        for (ItemDocumento item : documentoComItens.itensOrdenados()) {
            ItemDocumentoEntidade entidade = itens
                    .findByChaveAcessoAndNumeroItem(chaveAcesso, item.numeroItem())
                    .orElseGet(() -> new ItemDocumentoEntidade(
                            UUID.randomUUID(), chaveAcesso, item.numeroItem()));
            MapeadorDeDocumento.preencher(
                    entidade,
                    item,
                    HashDoItem.de(documentoComItens.documento().chaveAcesso(), item).valor());
            itens.save(entidade);
        }
    }

    /** Grava o apontamento e devolve o identificador da linha, nova ou reencontrada. */
    private UUID gravarAchado(AchadoLocalizado localizado, UUID execucaoId, Instant momento) {
        Achado achado = localizado.achado();
        String hashDoItem = localizado.hashDoItem().valor();

        AchadoEntidade entidade = achados
                .findByHashItemAndRegraIdAndRegraVersao(hashDoItem, achado.regraId(), achado.regraVersao())
                .orElseGet(() -> new AchadoEntidade(
                        UUID.randomUUID(),
                        hashDoItem,
                        achado.regraId(),
                        achado.regraVersao(),
                        execucaoId,
                        momento));

        entidade.registrarOcorrencia(
                achado.severidade(),
                achado.chaveAcesso().valor(),
                achado.numeroItem().orElseThrow(() -> new PersistenciaInconsistente(
                        "Apontamento sem número de item chegou à gravação; o serviço de auditoria "
                                + "deveria tê-lo recusado antes.")),
                achado.fundamentoNormativo(),
                achado.vigenciaAplicada().inicio(),
                achado.vigenciaAplicada().fim().orElse(null),
                achado.valorEmRisco().valor().orElse(null),
                achado.valorEmRisco().motivoDaAusencia().orElse(null),
                MapeadorDeAchado.paraEntidade(achado.evidencias()),
                execucaoId,
                momento);

        achados.save(entidade);
        return entidade.id();
    }
}
