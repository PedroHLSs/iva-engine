package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.aplicacao.conferencia.BaseTributariaEm;
import br.edu.tcc.auditoria.aplicacao.conferencia.ReferenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.time.LocalDate;
import java.util.List;

/**
 * A base normativa carregada, na data que se perguntou.
 *
 * <h2>A data volta escrita</h2>
 *
 * <p>Não existe "a base tributária" sem data, e por isso ela sai no corpo e não
 * só na URL: uma impressão desta tela continua dizendo a que dia se refere.</p>
 *
 * <h2>A resposta repete o que foi perguntado</h2>
 *
 * <p>{@code ncmPerguntado} e {@code classTribPerguntado} ecoam a consulta. É o
 * que distingue "ninguém perguntou por NCM" — bloco nulo, eco nulo — de
 * "perguntaram e a carga nada diz" — bloco presente, com motivo dentro. Sem o
 * eco, os dois casos chegariam iguais à tela.</p>
 *
 * <h2>A cobertura declarada abre a tela</h2>
 *
 * <p>Ela é o que separa "a carga não traz este registro" de "esta tabela não foi
 * carregada para esta data" (D004). Quem vai ler a base precisa dela antes de ler
 * qualquer linha, ou lerá silêncio como ausência.</p>
 */
public record RespostaDaBaseTributaria(
        LocalDate data,
        String versaoDoCatalogo,
        boolean cargaDisponivel,
        LeituraExposta<CoberturaExposta> cobertura,
        List<TratamentoExposto.TributoExposto> aliquotas,
        String ncmPerguntado,
        LeituraExposta<TratamentoExposto.NcmExposto> ncmConsultado,
        LeituraExposta<TratamentoExposto.EnquadramentoExposto> anexosDoNcm,
        String classTribPerguntado,
        LeituraExposta<TratamentoExposto.ClassificacaoExposta> classificacaoConsultada,
        String comoConsultar,
        FaixaDeNatureza natureza,
        String aviso) {

    static final String SEM_CARGA_PARA_COBERTURA =
            "não há carga de catálogo gravada, e por isso não há cobertura declarada a mostrar";

    public RespostaDaBaseTributaria {
        if (data == null) {
            throw new RespostaInvalida(
                    "A base tributária só existe datada: sem data a resposta não significa nada.");
        }
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new RespostaInvalida("A resposta precisa dizer de qual carga ela veio.");
        }
        if (cobertura == null) {
            throw new RespostaInvalida(
                    "A cobertura declarada precisa vir, ainda que seja o motivo de não haver nenhuma.");
        }
        if (cargaDisponivel == cobertura.encontrado().isEmpty()) {
            throw new RespostaInvalida(
                    "Ou a carga está gravada e a cobertura declarada dela vem junto, ou não está e não "
                            + "vem. As duas afirmações precisam concordar: uma tela que diz ter carga e "
                            + "não mostra cobertura faria silêncio do catálogo passar por ausência de "
                            + "registro.");
        }
        if (aliquotas == null || aliquotas.isEmpty()) {
            throw new RespostaInvalida(
                    "As alíquotas saem com um bloco por tributo, inclusive os que a carga não alcança.");
        }
        if ((ncmPerguntado == null) != (ncmConsultado == null)
                || (ncmPerguntado == null) != (anexosDoNcm == null)) {
            throw new RespostaInvalida(
                    "Ou se perguntou por um NCM e vêm as duas respostas dele, ou não se perguntou e não "
                            + "vem nenhuma. Bloco sem pergunta e pergunta sem bloco são igualmente "
                            + "confusos.");
        }
        if ((classTribPerguntado == null) != (classificacaoConsultada == null)) {
            throw new RespostaInvalida(
                    "Ou se perguntou por um cClassTrib e vem a resposta dele, ou não se perguntou.");
        }
        if (comoConsultar == null || comoConsultar.isBlank()) {
            throw new RespostaInvalida(
                    "A tela precisa dizer que consulta por NCM e por cClassTrib, e que não lista as "
                            + "tabelas inteiras. Sem isso, a ausência de listagem parece tela pela "
                            + "metade.");
        }
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência. Dado de demonstração "
                            + "sem aviso é afirmação falsa sobre a lei.");
        }
        if (aviso == null || aviso.isBlank()) {
            throw new RespostaInvalida("Toda resposta de resultado sai com o aviso de uso.");
        }
        aliquotas = List.copyOf(aliquotas);
    }

    /**
     * Monta a resposta, com o eco do que foi perguntado.
     *
     * <p>O eco vem de quem recebeu o pedido, e não é derivado da resposta de
     * propósito: derivá-lo faria um NCM que a carga não conhece voltar em branco,
     * e a tela mostraria "consultado: (nada)" logo acima do motivo dizendo que
     * aquele NCM não está na carga.</p>
     */
    static RespostaDaBaseTributaria de(
            BaseTributariaEm base, String ncmPerguntado, String classTribPerguntado) {

        return new RespostaDaBaseTributaria(
                base.data(),
                base.versaoDoCatalogo(),
                base.cargaDisponivel(),
                cobertura(base),
                base.aliquotas().stream().map(TratamentoExposto.TributoExposto::de).toList(),
                ncmPerguntado,
                base.ncmConsultado()
                        .map(leitura -> LeituraExposta.de(
                                leitura, TratamentoExposto.NcmExposto::de))
                        .orElse(null),
                base.anexosDoNcmConsultado()
                        .map(leitura -> LeituraExposta.de(
                                leitura, TratamentoExposto.EnquadramentoExposto::de))
                        .orElse(null),
                classTribPerguntado,
                base.classificacaoConsultada()
                        .map(leitura -> LeituraExposta.de(
                                leitura, TratamentoExposto.ClassificacaoExposta::de))
                        .orElse(null),
                BaseTributariaEm.COMO_CONSULTAR,
                FaixaDeNatureza.de(base.natureza(), base.versaoDoCatalogo()),
                AvisoDeUso.TEXTO);
    }

    private static LeituraExposta<CoberturaExposta> cobertura(BaseTributariaEm base) {
        return base.cobertura()
                .map(declarada -> new LeituraExposta<>(CoberturaExposta.de(declarada), (String) null))
                .orElseGet(() -> new LeituraExposta<>(List.of(), SEM_CARGA_PARA_COBERTURA));
    }

    /** O período e a fonte que a carga declarou cobrir, para uma tabela. */
    public record CoberturaExposta(String tabela, TratamentoExposto.ReferenciaExposta referencia) {

        public CoberturaExposta {
            if (tabela == null || tabela.isBlank()) {
                throw new RespostaInvalida("A cobertura precisa dizer de que tabela ela é.");
            }
            if (referencia == null) {
                throw new RespostaInvalida("A cobertura precisa do período e da fonte declarados.");
            }
        }

        static List<CoberturaExposta> de(CoberturaDoCatalogo cobertura) {
            return List.of(
                    uma(TabelaNormativa.CLASSIFICACAO_TRIBUTARIA, cobertura.classificacoesTributarias()),
                    uma(TabelaNormativa.NCM, cobertura.ncm()),
                    uma(TabelaNormativa.ITEM_ANEXO, cobertura.itensDeAnexo()));
        }

        private static CoberturaExposta uma(
                TabelaNormativa tabela, ProcedenciaNormativa procedencia) {
            return new CoberturaExposta(
                    tabela.name(),
                    TratamentoExposto.ReferenciaExposta.de(ReferenciaNormativa.de(procedencia)));
        }
    }
}
