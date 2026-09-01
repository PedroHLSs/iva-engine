package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.util.Optional;

/**
 * Como um apontamento aparece na planilha quanto a decisão humana.
 *
 * <p>Existe em vez de um {@code Optional<DecisaoDeTratativa>} porque a célula
 * precisa dizer alguma coisa nos três casos, e "em aberto" é uma informação, não
 * a falta de uma. Coluna vazia numa planilha lida meses depois é indistinguível
 * de coluna que ninguém preencheu.</p>
 */
public enum StatusDeTratativa {

    /** Ninguém decidiu ainda — ou a regra mudou de versão e o apontamento reabriu. */
    ABERTO,

    /** O auditor concordou com o apontamento. */
    ACEITO,

    /** O auditor sustentou que o documento está correto. */
    REFUTADO;

    /** Status correspondente à tratativa que houver. */
    public static StatusDeTratativa de(Optional<Tratativa> tratativa) {
        if (tratativa == null) {
            throw new PapelDeTrabalhoInvalido(
                    "Apontamento sem tratativa se representa com Optional.empty(), nunca com nulo.");
        }
        return tratativa.map(StatusDeTratativa::de).orElse(ABERTO);
    }

    private static StatusDeTratativa de(Tratativa tratativa) {
        return tratativa.decisao() == DecisaoDeTratativa.ACEITO ? ACEITO : REFUTADO;
    }
}
