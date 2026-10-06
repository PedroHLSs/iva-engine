package br.edu.tcc.auditoria.aplicacao.tratativa;

import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.util.Optional;
import java.util.UUID;

// Representa uma linha do histórico da tratativa: a decisão, quando foi dada e quem deu. Sem autor, vem o motivo de não haver; nunca os dois vazios.
public record RegistroDeTratativa(
        UUID id,
        Tratativa tratativa,
        Optional<AutorDaTratativa> autor,
        Optional<String> motivoDoAutorAusente) {

    // Valida que o registro tenha identificador, a tratativa, e o autor ou o motivo de faltar, nunca os dois.
    public RegistroDeTratativa {
        if (id == null || tratativa == null) {
            throw new TratativaInvalida("O registro de tratativa precisa de identificador e da decisão.");
        }
        if (autor == null || motivoDoAutorAusente == null) {
            throw new TratativaInvalida(
                    "Autor ausente se representa com Optional.empty() e o motivo ao lado, nunca com nulo.");
        }
        if (autor.isPresent() == motivoDoAutorAusente.isPresent()) {
            throw new TratativaInvalida(
                    "O registro de tratativa precisa do autor ou do motivo de não haver autor, e não dos "
                            + "dois: autor em branco sem explicação é indistinguível de autor apagado.");
        }
        motivoDoAutorAusente.ifPresent(motivo -> {
            if (motivo.isBlank()) {
                throw new TratativaInvalida("O motivo do autor ausente não pode ser vazio.");
            }
        });
    }
}
