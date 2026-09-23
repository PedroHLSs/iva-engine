package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.OrdemDosGrupos;

import java.util.Arrays;
import java.util.List;

// Representa a tela do lote: o resumo de leitura e de conferência, e os grupos de produtos por NCM, cClassTrib e situação. A ordem da lista vai junto com o que ela mede, porque a padrão mede o valor envolvido, e não a gravidade.
public record RespostaDeGrupos(
        String analiseId,
        ReciboDaAnalise leitura,
        ConferenciaExposta conferencia,
        OrdemExposta ordem,
        List<OrdemExposta> ordensDisponiveis,
        List<GrupoExposto> grupos,
        FaixaDeNatureza natureza,
        String aviso) {

    // Valida que a resposta tenha análise, os dois resumos, a ordem usada e as disponíveis, a lista de grupos, a faixa de procedência e o aviso de uso.
    public RespostaDeGrupos {
        if (analiseId == null || analiseId.isBlank()) {
            throw new RespostaInvalida("A tela de lote precisa dizer de que análise ela é.");
        }
        if (leitura == null || conferencia == null) {
            throw new RespostaInvalida(
                    "A tela de lote precisa dos dois blocos de resumo: arquivos e produtos.");
        }
        if (ordem == null) {
            throw new RespostaInvalida(
                    "A lista precisa dizer por que critério ela está ordenada, ou será lida como "
                            + "ranking de gravidade.");
        }
        if (ordensDisponiveis == null || ordensDisponiveis.isEmpty()) {
            throw new RespostaInvalida("A tela precisa oferecer as ordenações que existem.");
        }
        if (grupos == null) {
            throw new RespostaInvalida(
                    "A lista de grupos deve ser vazia quando a análise não leu produto nenhum, nunca "
                            + "nula.");
        }
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência. Dado de demonstração "
                            + "sem aviso é afirmação falsa sobre a lei.");
        }
        if (aviso == null || aviso.isBlank()) {
            throw new RespostaInvalida("Toda resposta de resultado sai com o aviso de uso.");
        }
        ordensDisponiveis = List.copyOf(ordensDisponiveis);
        grupos = List.copyOf(grupos);
    }

    // Método estático que devolve todas as ordenações que a tela pode oferecer, com o que cada uma significa.
    static List<OrdemExposta> todasAsOrdens() {
        return Arrays.stream(OrdemDosGrupos.values()).map(OrdemExposta::de).toList();
    }

    // Representa um jeito de ordenar os grupos e o que ele mede.
    public record OrdemExposta(String ordem, String rotulo, String significado, boolean padrao) {

        // Valida que a ordenação tenha código, rótulo e significado.
        public OrdemExposta {
            if (ordem == null || ordem.isBlank()
                    || rotulo == null || rotulo.isBlank()
                    || significado == null || significado.isBlank()) {
                throw new RespostaInvalida(
                        "Uma ordenação precisa do código, do rótulo e do significado. O significado é o "
                                + "que impede a lista de ser lida como ranking de gravidade.");
            }
        }

        // Método estático que converte a ordenação da aplicação e marca qual é a padrão.
        static OrdemExposta de(OrdemDosGrupos ordem) {
            return new OrdemExposta(
                    ordem.name(),
                    ordem.rotulo(),
                    ordem.significado(),
                    ordem == OrdemDosGrupos.padrao());
        }
    }
}
