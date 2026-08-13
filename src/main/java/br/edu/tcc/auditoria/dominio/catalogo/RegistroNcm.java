package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

/**
 * Existência e descrição de um NCM, numa dada vigência.
 *
 * <p>Serve para o catálogo poder responder "este NCM existia na data do
 * documento?" — pergunta que só faz sentido datada, já que a tabela de NCM muda
 * ao longo do tempo. A descrição vem da fonte importada; o domínio não conhece
 * nenhuma.</p>
 *
 * @param ncm         NCM a que o registro se refere
 * @param descricao   descrição trazida pela fonte
 * @param procedencia vigência e fonte
 */
public record RegistroNcm(Ncm ncm, String descricao, ProcedenciaNormativa procedencia)
        implements RegistroNormativo {

    public RegistroNcm {
        if (ncm == null) {
            throw new RegistroNormativoInvalido("O registro de NCM precisa de NCM.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "O registro do NCM \"%s\" precisa de descrição.".formatted(ncm.valor()));
        }
        if (procedencia == null) {
            throw new RegistroNormativoInvalido(
                    "O registro do NCM \"%s\" precisa de vigência e fonte normativa.".formatted(ncm.valor()));
        }
    }

    @Override
    public String chaveDeVigencia() {
        return ncm.valor();
    }
}
