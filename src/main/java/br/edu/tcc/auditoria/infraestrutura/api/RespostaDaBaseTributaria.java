package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.aplicacao.conferencia.BaseTributariaEm;
import br.edu.tcc.auditoria.aplicacao.conferencia.ReferenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.time.LocalDate;
import java.util.List;

// Representa a base normativa carregada na data consultada. A data e o que foi perguntado voltam na resposta, para "ninguém perguntou" não se confundir com "a carga não tem", e a cobertura declarada vem primeiro, para silêncio da tabela não ser lido como ausência.
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

    // Motivo escrito quando não há carga gravada e, portanto, nenhuma cobertura a mostrar.
    static final String SEM_CARGA_PARA_COBERTURA =
            "não há carga de catálogo gravada, e por isso não há cobertura declarada a mostrar";

    // Valida a resposta: exige data, versão, cobertura batendo com a carga, um bloco por tributo, pergunta e resposta andando juntas, o texto de como consultar, a faixa de procedência e o aviso de uso.
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

    // Método estático que monta a resposta com o que foi perguntado. A pergunta vem do pedido, e não da resposta, para um NCM que a carga não conhece não voltar em branco.
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

    // Método auxiliar que monta a cobertura declarada, ou o motivo de não haver carga.
    private static LeituraExposta<CoberturaExposta> cobertura(BaseTributariaEm base) {
        return base.cobertura()
                .map(declarada -> new LeituraExposta<>(CoberturaExposta.de(declarada), (String) null))
                .orElseGet(() -> new LeituraExposta<>(List.of(), SEM_CARGA_PARA_COBERTURA));
    }

    // Representa o período e a fonte que a carga declarou cobrir, para uma tabela.
    public record CoberturaExposta(String tabela, TratamentoExposto.ReferenciaExposta referencia) {

        // Valida que a cobertura tenha a tabela e a referência.
        public CoberturaExposta {
            if (tabela == null || tabela.isBlank()) {
                throw new RespostaInvalida("A cobertura precisa dizer de que tabela ela é.");
            }
            if (referencia == null) {
                throw new RespostaInvalida("A cobertura precisa do período e da fonte declarados.");
            }
        }

        // Método estático que monta a cobertura das três tabelas: classificação tributária, NCM e itens de anexo.
        static List<CoberturaExposta> de(CoberturaDoCatalogo cobertura) {
            return List.of(
                    uma(TabelaNormativa.CLASSIFICACAO_TRIBUTARIA, cobertura.classificacoesTributarias()),
                    uma(TabelaNormativa.NCM, cobertura.ncm()),
                    uma(TabelaNormativa.ITEM_ANEXO, cobertura.itensDeAnexo()));
        }

        // Método auxiliar que monta a cobertura de uma tabela.
        private static CoberturaExposta uma(
                TabelaNormativa tabela, ProcedenciaNormativa procedencia) {
            return new CoberturaExposta(
                    tabela.name(),
                    TratamentoExposto.ReferenciaExposta.de(ReferenciaNormativa.de(procedencia)));
        }
    }
}
