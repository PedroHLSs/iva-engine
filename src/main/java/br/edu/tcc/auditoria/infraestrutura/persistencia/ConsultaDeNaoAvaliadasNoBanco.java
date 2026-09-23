package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Classe que lê do banco as avaliações que não concluíram numa execução.
@Component
class ConsultaDeNaoAvaliadasNoBanco implements ConsultaDeNaoAvaliadas {

    private final AvaliacaoNaoConcluidaJpa naoConcluidas;

    // Construtor que recebe o repositório das avaliações não concluídas.
    ConsultaDeNaoAvaliadasNoBanco(AvaliacaoNaoConcluidaJpa naoConcluidas) {
        this.naoConcluidas = naoConcluidas;
    }

    // Busca as não concluídas da execução, ordenadas por chave, item e regra.
    @Override
    @Transactional(readOnly = true)
    public List<NaoAvaliadaRegistrada> daExecucao(UUID execucaoId) {
        if (execucaoId == null) {
            return List.of();
        }
        return naoConcluidas
                .findByExecucaoIdOrderByChaveAcessoAscNumeroItemAscRegraIdAsc(execucaoId).stream()
                .map(ConsultaDeNaoAvaliadasNoBanco::paraDominio)
                .toList();
    }

    // Método auxiliar que converte a linha gravada no modelo da aplicação.
    private static NaoAvaliadaRegistrada paraDominio(AvaliacaoNaoConcluidaEntidade entidade) {
        return new NaoAvaliadaRegistrada(
                new ChaveAcesso(entidade.chaveAcesso()),
                entidade.numeroItem(),
                entidade.regraId(),
                entidade.regraVersao(),
                entidade.motivo());
    }
}
