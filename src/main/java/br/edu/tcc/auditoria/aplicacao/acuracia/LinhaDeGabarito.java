package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;

/**
 * Uma afirmação do gabarito: nesta regra, sobre este item, a resposta certa é
 * esta.
 *
 * <p>Guarda o número da linha física do arquivo de onde veio, para que qualquer
 * recusa possa dizer onde corrigir — a mesma escolha do leitor de CSV do
 * catálogo, e pelo mesmo motivo: o gabarito é escrito à mão.</p>
 *
 * @param numeroDaLinha linha física no arquivo de gabarito, começando em 1
 * @param endereco      documento, item e regra a que a afirmação se refere
 * @param rotulo        o que a pessoa que rotulou afirmou
 */
public record LinhaDeGabarito(int numeroDaLinha, EnderecoDaAvaliacao endereco, RotuloEsperado rotulo) {

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

    /** Identificador da regra que esta linha rotula. */
    public String regraId() {
        return endereco.regraId();
    }
}
