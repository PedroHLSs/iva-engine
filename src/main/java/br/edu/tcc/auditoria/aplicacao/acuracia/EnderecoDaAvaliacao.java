package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.Optional;
//Classe representando o endereço de uma avaliação.
public record EnderecoDaAvaliacao(ChaveAcesso chaveAcesso, int numeroItem, String regraId) {

    public EnderecoDaAvaliacao {
        if (chaveAcesso == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O endereço da avaliação precisa da chave de acesso do documento.");
        }
        if (regraId == null || regraId.isBlank()) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O endereço da avaliação precisa do identificador da regra.");
        }
        if (numeroItem < 1) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        regraId = regraId.strip();
    }
    // Método estático que cria um EnderecoDaAvaliacao a partir de uma Avaliacao
    public static Optional<EnderecoDaAvaliacao> de(Avaliacao avaliacao) {
        if (avaliacao == null) {
            throw new AvaliacaoDeAcuraciaInvalida("Não há avaliação cujo endereço obter.");
        }
        if (avaliacao.numeroItem().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EnderecoDaAvaliacao(
                avaliacao.chaveAcesso(), avaliacao.numeroItem().getAsInt(), avaliacao.regraId()));
    }
}
