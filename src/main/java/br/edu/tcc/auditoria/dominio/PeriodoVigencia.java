package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.PeriodoVigenciaInvalido;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Intervalo de datas em que uma regra ou uma linha de tabela normativa vale.
 *
 * <p>O fim é opcional: vigência ainda aberta se representa com
 * {@code Optional.empty()}, não com uma data distante escolhida a esmo.</p>
 *
 * <p>Nenhuma data está fixada em código neste projeto. As datas de vigência são
 * conteúdo normativo e chegam junto com as tabelas importadas.</p>
 *
 * @param inicio primeiro dia de vigência, inclusive
 * @param fim    último dia de vigência, inclusive; vazio se ainda aberta
 */
public record PeriodoVigencia(LocalDate inicio, Optional<LocalDate> fim) {

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

    /** Vigência aberta: vale a partir de {@code inicio}, sem fim conhecido. */
    public static PeriodoVigencia aPartirDe(LocalDate inicio) {
        return new PeriodoVigencia(inicio, Optional.empty());
    }

    /** Vigência fechada, com os dois extremos inclusive. */
    public static PeriodoVigencia de(LocalDate inicio, LocalDate fim) {
        if (fim == null) {
            throw new PeriodoVigenciaInvalido(
                    "Para vigência sem fim conhecido use PeriodoVigencia.aPartirDe(inicio).");
        }
        return new PeriodoVigencia(inicio, Optional.of(fim));
    }

    /** Indica se a data cai dentro do período, considerando ambos os extremos inclusive. */
    public boolean contem(LocalDate data) {
        if (data == null) {
            throw new PeriodoVigenciaInvalido("A data consultada não pode ser nula.");
        }
        return !data.isBefore(inicio) && fim.map(ultimoDia -> !data.isAfter(ultimoDia)).orElse(true);
    }

    /** Indica se a vigência ainda está aberta, isto é, sem último dia conhecido. */
    public boolean estaAberta() {
        return fim.isEmpty();
    }
}
