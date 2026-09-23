package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaInvalida;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Classe que lê do banco os recibos das execuções gravadas.
@Component
class ConsultaDeExecucoesNoBanco implements ConsultaDeExecucoes {

    private final ExecucaoAuditoriaJpa execucoes;

    // Construtor que recebe o repositório de execuções.
    ConsultaDeExecucoesNoBanco(ExecucaoAuditoriaJpa execucoes) {
        this.execucoes = execucoes;
    }

    // Busca a execução mais recente, se houver.
    @Override
    @Transactional(readOnly = true)
    public Optional<ExecucaoAuditoria> maisRecente() {
        return execucoes.findAllByOrderByDataHoraDesc(PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(ConsultaDeExecucoesNoBanco::paraDominio);
    }

    // Busca uma execução pelo identificador.
    @Override
    @Transactional(readOnly = true)
    public Optional<ExecucaoAuditoria> porId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return execucoes.findById(id).map(ConsultaDeExecucoesNoBanco::paraDominio);
    }

    // Busca as execuções mais recentes, até a quantidade pedida; recusa quantidade menor que 1.
    @Override
    @Transactional(readOnly = true)
    public List<ExecucaoAuditoria> ultimas(int quantidade) {
        if (quantidade < 1) {
            throw new ConsultaInvalida(
                    "A quantidade de execuções a listar deve ser maior ou igual a 1, mas veio %d."
                            .formatted(quantidade));
        }
        return execucoes.findAllByOrderByDataHoraDesc(PageRequest.of(0, quantidade)).stream()
                .map(ConsultaDeExecucoesNoBanco::paraDominio)
                .toList();
    }

    // Método auxiliar que remonta o recibo com as contagens como foram gravadas, sem recalcular pelos apontamentos de hoje.
    private static ExecucaoAuditoria paraDominio(ExecucaoAuditoriaEntidade entidade) {
        return new ExecucaoAuditoria(
                entidade.id(),
                entidade.dataHora(),
                entidade.hashEntrada(),
                entidade.versaoCatalogo(),
                entidade.versaoConjuntoRegras(),
                entidade.quantidadeDocumentos(),
                entidade.quantidadeItens(),
                entidade.achadosPorSeveridade(),
                entidade.achadosPorRegra());
    }
}
