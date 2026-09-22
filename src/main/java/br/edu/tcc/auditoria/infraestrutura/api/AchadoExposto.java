package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Um apontamento como a API o mostra.
 *
 * <h2>{@code resultado} é escrito, não inferido</h2>
 *
 * <p>Toda linha carrega {@code "resultado": "ACHADO"}, e toda linha de
 * {@link NaoAvaliadaExposta} carrega {@code "NAO_AVALIADO"}. A distinção entre os
 * três desfechos fica textual em cada linha, e não posicional: quem consome não
 * precisa saber em qual array olhou para saber o que está lendo, e um recorte
 * copiado de uma resposta continua dizendo o que é.</p>
 *
 * <h2>{@code valorEmRisco} é texto, não número</h2>
 *
 * <p>Porque a escala declarada tem significado: para a auditoria, {@code 0} e
 * {@code 0,00} são registros diferentes do mesmo número (D002), e essa distinção
 * é preservada desde a leitura do XML. Um número JSON sobrevive ao servidor e
 * morre no cliente — {@code JSON.parse} de {@code 0.00} devolve {@code 0} —,
 * então o valor sai como texto, na notação simples, exatamente com as casas que o
 * documento declarou.</p>
 *
 * @param id           identificador da linha gravada, o mesmo que {@code tratar-achado} aceita
 * @param regraNome    a regra por extenso, ou {@code null} com o motivo ao lado; acrescentado
 *                     depois da Etapa 11, para a interface não escrever só o código (ver
 *                     {@link NomeDaRegra})
 * @param resultado    sempre {@code ACHADO}, escrito por extenso
 * @param valorEmRisco a quantia como texto, ou {@code null} com o motivo ao lado
 * @param detectadoEm  quando o apontamento apareceu pela primeira vez
 * @param vistoEm      quando o apontamento foi gerado pela última vez
 */
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

    /**
     * Uma evidência do apontamento.
     *
     * <p>{@code valorEncontrado} nulo é campo que o contribuinte não declarou;
     * {@code valorEsperado} nulo é regra sem valor de referência a opor. São
     * ausências de natureza diferente, e o que as distingue é a {@code origem},
     * que diz de onde a evidência veio.</p>
     */
    public record EvidenciaExposta(
            String campoAnalisado,
            String valorEncontrado,
            String valorEsperado,
            OrigemExposta origem) {

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

    /**
     * De onde a evidência veio.
     *
     * <p>{@code OrigemEvidencia} é tipo selado com três variantes que carregam
     * campos diferentes. Aqui elas viram um registro plano com {@code tipo} e os
     * campos de todas as variantes, os da variante que não se aplica em
     * {@code null}. É deliberado: preserva a estrutura em vez de achatá-la num
     * texto único, e mantém a regra de que campo ausente aparece.</p>
     */
    public record OrigemExposta(
            String tipo,
            String localizacao,
            String nomeTabela,
            String versaoTabela,
            String descricao) {

        public OrigemExposta {
            if (tipo == null || tipo.isBlank()) {
                throw new RespostaInvalida("A origem da evidência precisa do tipo.");
            }
        }
    }

    /**
     * A vigência que sustentou o apontamento.
     *
     * <p>{@code fim} nulo é vigência aberta, não vigência desconhecida — e o
     * motivo ao lado diz isso. É o mesmo tratamento que a planilha dá com
     * {@code (sem fim declarado)} (D007).</p>
     */
    public record VigenciaExposta(LocalDate inicio, LocalDate fim, String motivoDoFimAusente) {

        static final String SEM_FIM_DECLARADO =
                "vigência aberta: o catálogo não declarou data de fim para este registro";

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

        public static VigenciaExposta aberta(LocalDate inicio) {
            return new VigenciaExposta(inicio, null, SEM_FIM_DECLARADO);
        }

        public static VigenciaExposta fechada(LocalDate inicio, LocalDate fim) {
            return new VigenciaExposta(inicio, fim, null);
        }
    }

    /**
     * A decisão humana sobre o apontamento, quando há.
     *
     * <p>{@code justificativa} vem {@code null} por padrão, com o motivo ao lado.
     * É texto livre digitado por pessoa, sem sanitização, e pode conter CNPJ ou
     * razão social — o mesmo motivo pelo qual a página de figuras se recusa a ler
     * a coluna equivalente da planilha. Liga-se em
     * {@code auditoria.api.expor-justificativa} (D009).</p>
     *
     * <p>Apontamento sem tratativa não traz este objeto vazio: traz
     * {@code "tratativa": null}, e o {@code statusDeTratativa} do achado já diz
     * {@code ABERTO}.</p>
     */
    public record TratativaExposta(
            String decisao,
            Instant registradoEm,
            String justificativa,
            String motivoDaJustificativaOmitida) {

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

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new RespostaInvalida(
                    "O campo \"%s\" do achado exposto é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
