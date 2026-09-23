package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.util.Optional;

// Enum que diz como o apontamento aparece na planilha quanto à decisão humana; em aberto também é informação.
public enum StatusDeTratativa {

    // Ninguém decidiu ainda, ou a regra mudou de versão e o apontamento reabriu.
    ABERTO,

    // O auditor concordou com o apontamento.
    ACEITO,

    // O auditor sustentou que o documento está correto.
    REFUTADO;

    // Método estático que retorna o status correspondente à tratativa, ou ABERTO se não houver.
    public static StatusDeTratativa de(Optional<Tratativa> tratativa) {
        if (tratativa == null) {
            throw new PapelDeTrabalhoInvalido(
                    "Apontamento sem tratativa se representa com Optional.empty(), nunca com nulo.");
        }
        return tratativa.map(StatusDeTratativa::de).orElse(ABERTO);
    }

    // Método auxiliar que traduz a decisão da tratativa em ACEITO ou REFUTADO.
    private static StatusDeTratativa de(Tratativa tratativa) {
        return tratativa.decisao() == DecisaoDeTratativa.ACEITO ? ACEITO : REFUTADO;
    }
}
