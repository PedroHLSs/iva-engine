package br.edu.tcc.auditoria.dominio.tratativa;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Porta de acesso às tratativas registradas.
 *
 * <p>A busca é sempre pela chave completa — item, regra e versão da regra. Não
 * existe consulta que ignore a versão: ela abriria caminho para uma decisão
 * antiga silenciar um apontamento gerado por critério novo.</p>
 */
public interface RepositorioTratativa {

    /** Tratativa registrada para a chave, se houver. */
    Optional<Tratativa> buscar(ChaveDeTratativa chave);

    /**
     * Tratativas registradas para as chaves informadas.
     *
     * <p>Existe para que listar apontamentos não faça uma consulta por
     * apontamento. Chave sem tratativa simplesmente não aparece no resultado.</p>
     */
    Map<ChaveDeTratativa, Tratativa> buscarTodas(Collection<ChaveDeTratativa> chaves);

    /**
     * Grava a tratativa, substituindo a que existir para a mesma chave.
     *
     * <p>Reconsiderar uma decisão é registrar outra sobre a mesma chave: fica
     * valendo a última, com a justificativa dela.</p>
     */
    void salvar(Tratativa tratativa);
}
