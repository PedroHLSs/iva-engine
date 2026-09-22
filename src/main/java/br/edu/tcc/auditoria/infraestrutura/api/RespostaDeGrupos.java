package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.OrdemDosGrupos;

import java.util.Arrays;
import java.util.List;

/**
 * A tela do lote: o resumo, e os grupos por parametrização.
 *
 * <h2>O resumo do lote vem inteiro</h2>
 *
 * <p>{@code leitura} traz quantos arquivos entraram e quantos não puderam ser
 * abertos; {@code conferencia} traz os quatro estados dos produtos. Os dois
 * blocos ficam separados pelo mesmo motivo de sempre: arquivo que não pôde ser
 * lido não é nota sem divergência, é ausência, e não existe campo em que somá-lo
 * aos produtos.</p>
 *
 * <h2>A ordem vem com o significado dela</h2>
 *
 * <p>{@code ordem} diz por que critério a lista está ordenada e o que esse
 * critério mede — a padrão mede exposição, não gravidade. {@code ordensDisponiveis}
 * traz a alternativa, porque a pergunta "qual erro se repete mais" é outra
 * pergunta e merece outra ordenação.</p>
 */
public record RespostaDeGrupos(
        String analiseId,
        ReciboDaAnalise leitura,
        ConferenciaExposta conferencia,
        OrdemExposta ordem,
        List<OrdemExposta> ordensDisponiveis,
        List<GrupoExposto> grupos,
        FaixaDeNatureza natureza,
        String aviso) {

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

    /** Todas as ordenações que a tela pode oferecer, com o que cada uma significa. */
    static List<OrdemExposta> todasAsOrdens() {
        return Arrays.stream(OrdemDosGrupos.values()).map(OrdemExposta::de).toList();
    }

    /** Um critério de ordenação e o que ele mede. */
    public record OrdemExposta(String ordem, String rotulo, String significado, boolean padrao) {

        public OrdemExposta {
            if (ordem == null || ordem.isBlank()
                    || rotulo == null || rotulo.isBlank()
                    || significado == null || significado.isBlank()) {
                throw new RespostaInvalida(
                        "Uma ordenação precisa do código, do rótulo e do significado. O significado é o "
                                + "que impede a lista de ser lida como ranking de gravidade.");
            }
        }

        static OrdemExposta de(OrdemDosGrupos ordem) {
            return new OrdemExposta(
                    ordem.name(),
                    ordem.rotulo(),
                    ordem.significado(),
                    ordem == OrdemDosGrupos.padrao());
        }
    }
}
