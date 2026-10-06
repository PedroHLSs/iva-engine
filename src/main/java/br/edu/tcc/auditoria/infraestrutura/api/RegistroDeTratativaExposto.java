package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.tratativa.AutorDaTratativa;
import br.edu.tcc.auditoria.aplicacao.tratativa.RegistroDeTratativa;

import java.time.Instant;

// Representa uma linha do histórico de tratativa como a API mostra: a decisão, quando, quem decidiu e a justificativa. A justificativa segue a mesma política de sempre: omitida com o motivo, a não ser que a instalação ligue auditoria.api.expor-justificativa. Autor ausente vem null com o motivo.
public record RegistroDeTratativaExposto(
        String id,
        String decisao,
        Instant registradoEm,
        String regraId,
        String regraVersao,
        AutorExposto autor,
        String motivoDoAutorAusente,
        String justificativa,
        String motivoDaJustificativaOmitida) {

    // Representa quem decidiu. Usuário desativado continua aparecendo, marcado como tal.
    public record AutorExposto(String id, String login, String nome, boolean ativo) {

        // Método estático que monta o autor exposto.
        static AutorExposto de(AutorDaTratativa autor) {
            return new AutorExposto(autor.id().toString(), autor.login(), autor.nome(), autor.ativo());
        }
    }

    // Valida que o autor venha ou com o motivo de faltar, e a justificativa ou com o motivo de estar omitida.
    public RegistroDeTratativaExposto {
        if ((autor == null) == (motivoDoAutorAusente == null)) {
            throw new RespostaInvalida("O registro de tratativa traz o autor ou o motivo de não haver, e não os dois.");
        }
        if ((justificativa == null) == (motivoDaJustificativaOmitida == null)) {
            throw new RespostaInvalida(
                    "O registro de tratativa traz a justificativa ou o motivo de estar omitida, e não os dois.");
        }
    }

    // Método estático que monta o registro exposto, respeitando a política de exposição da justificativa.
    static RegistroDeTratativaExposto de(RegistroDeTratativa registro, PoliticaDeExposicao politica) {
        String justificativa = politica.justificativaOuNulo(registro.tratativa().justificativa());
        return new RegistroDeTratativaExposto(
                registro.id().toString(),
                registro.tratativa().decisao().name(),
                registro.tratativa().registradoEm(),
                registro.tratativa().regraId(),
                registro.tratativa().regraVersao(),
                registro.autor().map(AutorExposto::de).orElse(null),
                registro.motivoDoAutorAusente().orElse(null),
                justificativa,
                justificativa == null ? politica.motivoDaJustificativaOmitida() : null);
    }
}
