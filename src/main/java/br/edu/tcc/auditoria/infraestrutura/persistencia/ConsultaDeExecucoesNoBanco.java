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

/** Lê os recibos de execução gravados. */
@Component
class ConsultaDeExecucoesNoBanco implements ConsultaDeExecucoes {

    private final ExecucaoAuditoriaJpa execucoes;

    ConsultaDeExecucoesNoBanco(ExecucaoAuditoriaJpa execucoes) {
        this.execucoes = execucoes;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExecucaoAuditoria> maisRecente() {
        return execucoes.findAllByOrderByDataHoraDesc(PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(ConsultaDeExecucoesNoBanco::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExecucaoAuditoria> porId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return execucoes.findById(id).map(ConsultaDeExecucoesNoBanco::paraDominio);
    }

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

    /**
     * Reconstrói o recibo com as contagens como foram gravadas.
     *
     * <p>Pelo construtor, e não pela fábrica {@code ExecucaoAuditoria.de(...)}:
     * as contagens vêm do banco e não devem ser recalculadas a partir dos
     * apontamentos de hoje. O que a execução contou naquele dia é o que ela
     * contou, e é isso que o papel de trabalho tem de mostrar.</p>
     */
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
