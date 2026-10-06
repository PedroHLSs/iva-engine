package br.edu.tcc.auditoria.aplicacao.historico;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.time.LocalDate;
import java.util.Optional;

// Representa o que a pessoa pediu para ver no histórico: período, situação mais grave presente, faixa de produtos com possível divergência, executor, e a página. Campo vazio quer dizer "sem filtro".
public record FiltroDoHistorico(
        Optional<LocalDate> de,
        Optional<LocalDate> ate,
        Optional<EstadoDeConferencia> situacaoMaisGrave,
        Optional<Integer> minimoDeDivergencias,
        Optional<Integer> maximoDeDivergencias,
        FiltroDeExecutor executor,
        int pagina,
        int tamanho) {

    // Maior página que o histórico entrega de uma vez.
    public static final int TAMANHO_MAXIMO = 100;

    // Valida o filtro: opcionais não nulos, período e faixa coerentes, página e tamanho dentro do limite.
    public FiltroDoHistorico {
        if (de == null || ate == null || situacaoMaisGrave == null || minimoDeDivergencias == null
                || maximoDeDivergencias == null || executor == null) {
            throw new HistoricoInvalido("Filtro não usado se representa com vazio, nunca com nulo.");
        }
        if (de.isPresent() && ate.isPresent() && de.get().isAfter(ate.get())) {
            throw new HistoricoInvalido(
                    "O período começa em %s e termina em %s: o início vem depois do fim."
                            .formatted(de.get(), ate.get()));
        }
        if (minimoDeDivergencias.filter(minimo -> minimo < 0).isPresent()
                || maximoDeDivergencias.filter(maximo -> maximo < 0).isPresent()) {
            throw new HistoricoInvalido("A quantidade de produtos com possível divergência não pode ser negativa.");
        }
        if (minimoDeDivergencias.isPresent() && maximoDeDivergencias.isPresent()
                && minimoDeDivergencias.get() > maximoDeDivergencias.get()) {
            throw new HistoricoInvalido(
                    "O mínimo de produtos com possível divergência (%d) é maior que o máximo (%d)."
                            .formatted(minimoDeDivergencias.get(), maximoDeDivergencias.get()));
        }
        if (pagina < 0) {
            throw new HistoricoInvalido("A página não pode ser negativa; a primeira é 0.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new HistoricoInvalido(
                    "O tamanho da página vai de 1 a %d, mas veio %d.".formatted(TAMANHO_MAXIMO, tamanho));
        }
    }

    // Representa o filtro por quem executou: qualquer um, um login, ou só as execuções sem executor registrado.
    public record FiltroDeExecutor(Optional<String> login, boolean somenteNaoRegistrado) {

        // Valida que os dois não venham juntos.
        public FiltroDeExecutor {
            if (login == null) {
                throw new HistoricoInvalido("Login não usado se representa com vazio, nunca com nulo.");
            }
            if (login.isPresent() && somenteNaoRegistrado) {
                throw new HistoricoInvalido(
                        "Não dá para filtrar por um executor e, ao mesmo tempo, pelas execuções sem executor.");
            }
        }

        // Método estático do filtro que aceita qualquer executor.
        public static FiltroDeExecutor qualquer() {
            return new FiltroDeExecutor(Optional.empty(), false);
        }
    }
}
