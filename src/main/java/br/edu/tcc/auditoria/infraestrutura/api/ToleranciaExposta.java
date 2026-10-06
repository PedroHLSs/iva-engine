package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;

import java.util.Optional;

// Representa a tolerância de valor da R05 que a execução usou (D023): a quantia como texto, para a escala não se perder no JSON, a origem em código e o texto por extenso, com o padrão dito como padrão. Execução anterior ao registro vem com quantia, origem e texto nulos e o motivo ao lado — o par da D009.
public record ToleranciaExposta(String quantia, String origem, String texto, String motivoDaAusencia) {

    // Valida o par: tolerância presente ou motivo da ausência, nunca os dois.
    public ToleranciaExposta {
        if ((quantia == null) == (motivoDaAusencia == null)) {
            throw new RespostaInvalida("A tolerância vem presente ou com o motivo de faltar, nunca os dois.");
        }
        if (quantia != null && (origem == null || texto == null)) {
            throw new RespostaInvalida("A tolerância presente vem com a origem e o texto.");
        }
    }

    // Método estático que converte a tolerância registrada, ou a ausência dela.
    static ToleranciaExposta de(Optional<ToleranciaDaExecucao> registrada) {
        return registrada
                .map(tolerancia -> new ToleranciaExposta(
                        tolerancia.quantia(), tolerancia.origem().name(), tolerancia.texto(), null))
                .orElseGet(() -> new ToleranciaExposta(null, null, null, ToleranciaDaExecucao.NAO_REGISTRADA));
    }
}
