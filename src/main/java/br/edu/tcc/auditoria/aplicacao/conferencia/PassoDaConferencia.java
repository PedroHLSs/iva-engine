package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Representa uma linha do "por que este resultado": a regra, o estado a que ela chegou e a explicação tirada do que foi gravado.
public record PassoDaConferencia(
        String regraId,
        EstadoDeConferencia estado,
        VersaoDaRegra versao,
        ExplicacaoDaVerificacao explicacao) {

    // Valida que o passo tenha regra, estado, versão e explicação.
    public PassoDaConferencia {
        if (regraId == null || regraId.isBlank()) {
            throw new ConferenciaInvalida("O passo precisa dizer de que regra ele é.");
        }
        if (estado == null) {
            throw new ConferenciaInvalida(
                    "O passo da regra %s precisa do estado a que ela chegou.".formatted(regraId));
        }
        if (versao == null) {
            throw new ConferenciaInvalida(
                    "O passo da regra %s precisa da versão, registrada ou com o motivo de não estar."
                            .formatted(regraId));
        }
        if (explicacao == null) {
            throw new ConferenciaInvalida(
                    ("O passo da regra %s precisa da explicação. Um passo sem razão é a linha que a "
                            + "pessoa lê e continua sem saber o que aconteceu.").formatted(regraId));
        }
    }

    // Método estático que monta um passo por verificação, na ordem das verificações do produto.
    public static List<PassoDaConferencia> de(ProdutoConferido produto) {
        if (produto == null) {
            throw new ConferenciaInvalida("Não há produto cujo resultado explicar.");
        }

        List<PassoDaConferencia> passos = new ArrayList<>();
        for (VerificacaoDoProduto verificacao : produto.situacao().verificacoes()) {
            passos.add(new PassoDaConferencia(
                    verificacao.regraId(),
                    verificacao.estado(),
                    verificacao.versao(),
                    explicar(verificacao.regraId(), produto)));
        }
        return List.copyOf(passos);
    }

    // Método auxiliar que escolhe a explicação: pelo apontamento gravado, pela pendência gravada ou, se não houver nenhum, pela derivação.
    private static ExplicacaoDaVerificacao explicar(String regraId, ProdutoConferido produto) {
        Optional<AchadoRegistrado> apontado = produto.achados().stream()
                .filter(registrado -> registrado.achado().regraId().equals(regraId))
                .findFirst();
        if (apontado.isPresent()) {
            return ExplicacaoDaVerificacao.PorApontamento.de(apontado.orElseThrow().achado());
        }

        Optional<NaoAvaliadaRegistrada> pendente = produto.naoAvaliadas().stream()
                .filter(pendencia -> pendencia.regraId().equals(regraId))
                .findFirst();
        if (pendente.isPresent()) {
            return new ExplicacaoDaVerificacao.PorPendencia(pendente.orElseThrow().motivo());
        }

        return ExplicacaoDaVerificacao.PorDerivacao.daRegra(regraId);
    }
}
