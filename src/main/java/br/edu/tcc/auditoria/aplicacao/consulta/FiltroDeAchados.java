package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Severidade;

import java.util.Optional;

/**
 * Recorte de uma listagem de apontamentos.
 *
 * <p>Todo critério é opcional; filtro sem nenhum critério lista tudo, do mais
 * grave para o menos grave. O limite existe porque um lote real produz
 * apontamento demais para caber num terminal, e não porque o resto seja
 * descartável — quem precisa do relatório inteiro pede um limite maior.</p>
 *
 * @param apenasAbertos quando verdadeiro, omite os apontamentos que já têm
 *                      tratativa aplicável
 */
public record FiltroDeAchados(
        Optional<Severidade> severidade,
        Optional<String> regraId,
        Optional<ChaveAcesso> chaveAcesso,
        boolean apenasAbertos,
        int limite) {

    /** Limite usado quando quem consulta não informa um. */
    public static final int LIMITE_PADRAO = 50;

    public FiltroDeAchados {
        exigirOptional(severidade, "severidade");
        exigirOptional(regraId, "regraId");
        exigirOptional(chaveAcesso, "chaveAcesso");
        regraId.ifPresent(identificador -> {
            if (identificador.isBlank()) {
                throw new ConsultaInvalida(
                        "O filtro por regra veio vazio. Para não filtrar por regra, use Optional.empty().");
            }
        });
        if (limite < 1) {
            throw new ConsultaInvalida(
                    "O limite da listagem deve ser maior ou igual a 1, mas veio %d.".formatted(limite));
        }
    }

    /** Filtro sem nenhum critério, com o limite padrão. */
    public static FiltroDeAchados tudo() {
        return new FiltroDeAchados(
                Optional.empty(), Optional.empty(), Optional.empty(), false, LIMITE_PADRAO);
    }

    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new ConsultaInvalida(
                    "O critério \"%s\" deve ser Optional.empty() quando não informado, nunca nulo."
                            .formatted(nomeDoCampo));
        }
    }
}
