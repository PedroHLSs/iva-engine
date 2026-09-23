package br.edu.tcc.auditoria.aplicacao.consulta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Interface responsável por consultar os apontamentos gravados, trazendo junto a tratativa aplicável de cada um.
public interface ConsultaDeAchados {

    // Retorna os apontamentos que atendem ao filtro, do mais grave para o menos grave.
    List<AchadoRegistrado> listar(FiltroDeAchados filtro);

    // Retorna um apontamento pelo identificador, ou vazio se não existe.
    Optional<AchadoRegistrado> porId(UUID id);

    // Retorna a quantidade total de apontamentos que atendem ao filtro, ignorando o limite.
    long contar(FiltroDeAchados filtro);
}
