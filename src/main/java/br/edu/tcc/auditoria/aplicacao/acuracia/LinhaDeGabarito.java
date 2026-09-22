package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;

// Representa uma linha do gabarito, que contém o número da linha, o endereço da avaliação e o rótulo esperado.
public record LinhaDeGabarito(int numeroDaLinha, EnderecoDaAvaliacao endereco, RotuloEsperado rotulo) {
    // Construtor que valida os parâmetros da linha do gabarito, garantindo que não sejam nulos e que o número da linha seja válido.
    public LinhaDeGabarito {
        if (endereco == null) {
            throw new AvaliacaoDeAcuraciaInvalida("A linha do gabarito precisa do endereço que ela rotula.");
        }
        if (rotulo == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A linha do gabarito precisa do rótulo esperado: uma linha sem rótulo não afirma nada "
                            + "e não pode ser medida.");
        }
        if (numeroDaLinha < 1) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O número da linha do gabarito começa em 1, mas veio %d.".formatted(numeroDaLinha));
        }
    }

    public String regraId() {
        return endereco.regraId();
    }
}
