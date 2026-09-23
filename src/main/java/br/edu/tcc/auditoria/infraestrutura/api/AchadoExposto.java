package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Representa um apontamento como a API mostra. O resultado ACHADO vai escrito em toda linha, e o valor em risco vai como texto, para as casas decimais da nota não se perderem no JSON; sem valor, vem null com o motivo.
public record AchadoExposto(
        String id,
        DocumentoExposto documento,
        int numeroItem,
        String regraId,
        String regraNome,
        String motivoDoNomeDaRegraAusente,
        String regraVersao,
        String severidade,
        String resultado,
        List<EvidenciaExposta> evidencias,
        String fundamentoNormativo,
        VigenciaExposta vigenciaAplicada,
        String valorEmRisco,
        String motivoDoValorAusente,
        Instant detectadoEm,
        Instant vistoEm,
        String statusDeTratativa,
        TratativaExposta tratativa) {

    // Valida o apontamento: exige documento, vigência, campos de texto, item, pelo menos uma evidência e o valor em risco ou o motivo de faltar.
    public AchadoExposto {
        if (documento == null || vigenciaAplicada == null) {
            throw new RespostaInvalida(
                    "O achado exposto precisa do documento e da vigência aplicada.");
        }
        exigirTexto(id, "id");
        exigirTexto(regraId, "regraId");
        NomeDaRegra.exigirPar(regraNome, motivoDoNomeDaRegraAusente, regraId);
        exigirTexto(regraVersao, "regraVersao");
        exigirTexto(severidade, "severidade");
        exigirTexto(resultado, "resultado");
        exigirTexto(fundamentoNormativo, "fundamentoNormativo");
        exigirTexto(statusDeTratativa, "statusDeTratativa");

        if (numeroItem < 1) {
            throw new RespostaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (evidencias == null || evidencias.isEmpty()) {
            throw new RespostaInvalida(
                    "Um apontamento precisa de ao menos uma evidência: sem evidência não há o que "
                            + "conferir.");
        }
        if (valorEmRisco == null && (motivoDoValorAusente == null || motivoDoValorAusente.isBlank())) {
            throw new RespostaInvalida(
                    "Sem valor em risco, o achado precisa dizer por que não há. É a mesma exigência que "
                            + "ValorEmRisco.NaoCalculavel faz no domínio.");
        }
        if (valorEmRisco != null && motivoDoValorAusente != null) {
            throw new RespostaInvalida(
                    "O valor em risco não pode estar presente e ausente ao mesmo tempo.");
        }
        evidencias = List.copyOf(evidencias);
    }

    // Representa uma evidência do apontamento. Valor encontrado null é campo que a nota não trouxe; valor esperado null é regra sem valor de referência.
    public record EvidenciaExposta(
            String campoAnalisado,
            String valorEncontrado,
            String valorEsperado,
            OrigemExposta origem) {

        // Valida que a evidência tenha o campo analisado e a origem.
        public EvidenciaExposta {
            if (campoAnalisado == null || campoAnalisado.isBlank()) {
                throw new RespostaInvalida("A evidência precisa dizer qual campo foi analisado.");
            }
            if (origem == null) {
                throw new RespostaInvalida(
                        "A evidência precisa registrar sua origem para ser rastreável.");
            }
        }
    }

    // Representa de onde a evidência veio, com o tipo e os campos de todas as origens possíveis; os que não se aplicam vêm null.
    public record OrigemExposta(
            String tipo,
            String localizacao,
            String nomeTabela,
            String versaoTabela,
            String descricao) {

        // Valida que a origem tenha o tipo.
        public OrigemExposta {
            if (tipo == null || tipo.isBlank()) {
                throw new RespostaInvalida("A origem da evidência precisa do tipo.");
            }
        }
    }

    // Representa o período que valia para o apontamento. Fim null quer dizer período ainda aberto, e o motivo ao lado diz isso.
    public record VigenciaExposta(LocalDate inicio, LocalDate fim, String motivoDoFimAusente) {

        // Motivo escrito quando o catálogo não declarou data de fim.
        static final String SEM_FIM_DECLARADO =
                "vigência aberta: o catálogo não declarou data de fim para este registro";

        // Valida que haja início e que o fim venha ou com data ou com o motivo, nunca os dois.
        public VigenciaExposta {
            if (inicio == null) {
                throw new RespostaInvalida("A vigência aplicada precisa da data de início.");
            }
            if (fim == null && (motivoDoFimAusente == null || motivoDoFimAusente.isBlank())) {
                throw new RespostaInvalida(
                        "Vigência sem fim precisa dizer que é aberta. Data em branco sem explicação é "
                                + "indistinguível de data que ninguém preencheu.");
            }
            if (fim != null && motivoDoFimAusente != null) {
                throw new RespostaInvalida(
                        "A vigência não pode ter fim declarado e motivo de fim ausente ao mesmo tempo.");
            }
        }

        // Método estático que cria um período aberto, sem fim.
        public static VigenciaExposta aberta(LocalDate inicio) {
            return new VigenciaExposta(inicio, null, SEM_FIM_DECLARADO);
        }

        // Método estático que cria um período com início e fim.
        public static VigenciaExposta fechada(LocalDate inicio, LocalDate fim) {
            return new VigenciaExposta(inicio, fim, null);
        }
    }

    // Representa a decisão de uma pessoa sobre o apontamento. A justificativa vem null com o motivo, porque é texto livre e pode ter CNPJ ou razão social; só aparece se a instalação ligar auditoria.api.expor-justificativa.
    public record TratativaExposta(
            String decisao,
            Instant registradoEm,
            String justificativa,
            String motivoDaJustificativaOmitida) {

        // Valida que haja decisão e data de registro, e a justificativa ou o motivo de estar omitida, nunca os dois.
        public TratativaExposta {
            if (decisao == null || decisao.isBlank() || registradoEm == null) {
                throw new RespostaInvalida(
                        "A tratativa exposta precisa da decisão e de quando foi registrada.");
            }
            if (justificativa == null
                    && (motivoDaJustificativaOmitida == null
                            || motivoDaJustificativaOmitida.isBlank())) {
                throw new RespostaInvalida(
                        "Justificativa omitida sem motivo. Um apontamento tratado sem razão visível "
                                + "parece apontamento apagado.");
            }
            if (justificativa != null && motivoDaJustificativaOmitida != null) {
                throw new RespostaInvalida(
                        "A justificativa não pode estar presente e omitida ao mesmo tempo.");
            }
        }
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new RespostaInvalida(
                    "O campo \"%s\" do achado exposto é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
