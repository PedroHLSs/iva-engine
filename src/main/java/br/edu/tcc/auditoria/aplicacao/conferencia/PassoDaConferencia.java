package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uma linha do "por que este resultado": uma regra, o que ela concluiu, e a
 * razão disso.
 *
 * <h2>A explicação sai do que foi gravado, não de um texto por regra</h2>
 *
 * <p>Não existe neste projeto uma tabela que diga "R03 significa isto". A frase
 * de cada passo vem das evidências que o apontamento carrega, do motivo que a
 * regra escreveu ao não concluir, ou da conta que derivou o conforme. É o que a
 * torna específica daquele produto: o campo examinado, o valor encontrado, o
 * valor oposto e de onde ele veio.</p>
 *
 * <p>A consequência é que a explicação acompanha a regra sem ninguém manter
 * nada em dia. Se uma regra mudar a evidência que grava, a tela muda junto. Um
 * texto decorado ficaria para trás em silêncio, dizendo sobre a regra de ontem
 * o que a de hoje não faz mais.</p>
 *
 * <h2>A ordem é a das verificações</h2>
 *
 * <p>{@link SituacaoDoProduto} ordena por identificador de regra, e os passos
 * saem na mesma ordem. Duas aberturas da mesma nota mostram a mesma sequência —
 * conferência que muda de ordem a cada carregamento não é conferível.</p>
 */
public record PassoDaConferencia(
        String regraId,
        EstadoDeConferencia estado,
        VersaoDaRegra versao,
        ExplicacaoDaVerificacao explicacao) {

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

    /**
     * Os passos de um produto, um por verificação, na ordem das verificações.
     *
     * <p>Cada verificação já sabe a que estado chegou; o que se procura aqui é a
     * procedência dela entre o que foi gravado. Regra com apontamento gravado
     * explica por ele; regra com pendência gravada explica por ela; o que sobra é,
     * por construção, o conforme derivado — que é a mesma subtração feita em
     * {@link MontadorDaConferencia}, e a razão de as duas classes precisarem
     * concordar.</p>
     */
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
