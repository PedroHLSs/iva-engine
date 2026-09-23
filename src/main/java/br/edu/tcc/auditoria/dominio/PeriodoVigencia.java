package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.PeriodoVigenciaInvalido;

import java.time.LocalDate;
import java.util.Optional;

// Representa o período em que uma regra ou uma linha de tabela vale. Fim vazio quer dizer que ainda está valendo; nenhuma data fica fixa no código.
public record PeriodoVigencia(LocalDate inicio, Optional<LocalDate> fim) {

    // Valida que exista início e que o fim, se houver, não seja antes do início.
    public PeriodoVigencia {
        if (inicio == null) {
            throw new PeriodoVigenciaInvalido("O início da vigência não pode ser nulo.");
        }
        if (fim == null) {
            throw new PeriodoVigenciaInvalido(
                    "O fim da vigência deve ser Optional.empty() quando a vigência é aberta, nunca nulo.");
        }
        if (fim.isPresent() && fim.get().isBefore(inicio)) {
            throw new PeriodoVigenciaInvalido(
                    "O fim da vigência (%s) é anterior ao início (%s).".formatted(fim.get(), inicio));
        }
    }

    // Método estático que cria um período que vale a partir da data, sem fim.
    public static PeriodoVigencia aPartirDe(LocalDate inicio) {
        return new PeriodoVigencia(inicio, Optional.empty());
    }

    // Método estático que cria um período com início e fim, contando os dois dias.
    public static PeriodoVigencia de(LocalDate inicio, LocalDate fim) {
        if (fim == null) {
            throw new PeriodoVigenciaInvalido(
                    "Para vigência sem fim conhecido use PeriodoVigencia.aPartirDe(inicio).");
        }
        return new PeriodoVigencia(inicio, Optional.of(fim));
    }

    // Indica se a data está dentro do período, contando o primeiro e o último dia.
    public boolean contem(LocalDate data) {
        if (data == null) {
            throw new PeriodoVigenciaInvalido("A data consultada não pode ser nula.");
        }
        return !data.isBefore(inicio) && fim.map(ultimoDia -> !data.isAfter(ultimoDia)).orElse(true);
    }

    // Indica se o período ainda está aberto, sem último dia. Ainda não é usado em produção.
    public boolean estaAberta() {
        return fim.isEmpty();
    }
}
