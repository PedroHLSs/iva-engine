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

// Repositório que grava uma rodada de auditoria inteira: execução, documentos, itens, apontamentos e não concluídas, nessa ordem, por causa das chaves estrangeiras. Documento, item e apontamento são gravados pela identidade natural, então reprocessar o lote não duplica nada e a tratativa continua valendo; só a execução e as não concluídas são sempre novas.
@Repository
class RepositorioDaAuditoriaNoBanco implements RepositorioDaAuditoria {

    private final ExecucaoAuditoriaJpa execucoes;
    private final DocumentoJpa documentos;
    private final ItemDocumentoJpa itens;
    private final AchadoJpa achados;
    private final AchadoDaExecucaoJpa achadosDaExecucao;
    private final AvaliacaoNaoConcluidaJpa naoConcluidas;

    // Construtor que recebe os repositórios de execução, documento, item, apontamento, vínculo e não concluídas.
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

    // Grava a execução, os documentos com os itens, os apontamentos com o vínculo à execução e as não concluídas.
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

    // Método auxiliar que grava as avaliações não concluídas da execução, sempre como linhas novas, porque cada execução tem as suas.
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

    // Método auxiliar que grava o recibo da execução.
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

    // Método auxiliar que grava o documento e os itens, atualizando as linhas que já existem.
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

    // Método auxiliar que grava o apontamento, atualizando o que já existe com a mesma chave, e devolve o identificador da linha.
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
