package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura dos apontamentos gravados.
 *
 * <p>Quem implementa é responsável por trazer a tratativa aplicável junto de
 * cada apontamento, e não depois: um relatório que lista apontamento sem dizer
 * que ele já foi tratado faz a mesma pessoa reexaminar a mesma coisa a cada
 * rodada.</p>
 */
public interface ConsultaDeAchados {

    /** Apontamentos que atendem ao filtro, do mais grave para o menos grave. */
    List<AchadoRegistrado> listar(FiltroDeAchados filtro);

    /** Um apontamento pelo identificador, vazio se não existe. */
    Optional<AchadoRegistrado> porId(UUID id);

    /** Quantidade total de apontamentos que atendem ao filtro, ignorando o limite. */
    long contar(FiltroDeAchados filtro);
}
