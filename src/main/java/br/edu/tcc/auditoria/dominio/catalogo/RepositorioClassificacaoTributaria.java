package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Porta de acesso às classificações tributárias do catálogo.
 *
 * <p>Aqui a data é parâmetro, e é assim de propósito: o repositório é o lugar
 * onde a resolução no tempo acontece. Quem escreve regra de auditoria não fala
 * com esta interface — fala com {@link ContextoNormativo}, que já vem preso à
 * data do documento e não aceita data nenhuma.</p>
 */
public interface RepositorioClassificacaoTributaria {

    /**
     * A versão do código que valia na data, se alguma valia.
     *
     * <p>Vazio significa que o catálogo nada diz sobre este código nesta data —
     * seja porque o código não existe, seja porque a data cai fora de toda
     * vigência registrada. As duas situações são igualmente "não sei", e a
     * regra que consultar deve tratá-las como {@code NAO_AVALIADO}, nunca como
     * conformidade.</p>
     */
    Optional<ClassificacaoTributaria> buscarVigenteEm(CodigoClassificacaoTributaria codigo, LocalDate data);
}
