package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O que o documento declarou, ao lado do que a base normativa indica, campo a
 * campo.
 *
 * <h2>Esta tela não emite um segundo veredito</h2>
 *
 * <p>A comparação põe os dois lados um do lado do outro e <strong>para
 * aí</strong>. Ela não escreve "confere" nem "não confere", e a omissão é
 * deliberada.</p>
 *
 * <p>Quem julga se o declarado procede são as sete regras, e o julgamento delas
 * já está na situação do produto. Uma comparação que emitisse veredito próprio
 * seria um oitavo juízo, mais fraco que os outros sete: ignoraria a tolerância de
 * valor, ignoraria cobertura declarada da carga e ignoraria as condições que cada
 * regra examina. Na prática ela acabaria dizendo "diferente" onde a regra
 * concluiu sem violação — e a pessoa veria a interface contradizendo o motor, sem
 * ter como saber qual dos dois está certo.</p>
 *
 * <p>O que a comparação afirma é só sobre presença: quando um dos lados não
 * existe, ela diz qual e por quê. Isso é fato sobre o documento e sobre a carga,
 * não sobre a norma.</p>
 *
 * <h2>Os números vão como texto, com a escala declarada</h2>
 *
 * <p>Pelo mesmo motivo da D009: a diferença entre um percentual declarado com
 * duas casas e um com quatro é informação, e some no primeiro arredondamento.</p>
 */
public record ComparacaoDeclaradoEIndicado(List<Linha> linhas) {

    /** O que a tela escreve quando as regras é que respondem pelo veredito. */
    public static final String QUEM_JULGA =
            "A conferência das regras sobre este produto está na situação acima e nos passos abaixo. "
                    + "Este quadro só põe lado a lado o que o documento declarou e o que a base "
                    + "normativa carregada indica.";

    public ComparacaoDeclaradoEIndicado {
        if (linhas == null || linhas.isEmpty()) {
            throw new ConferenciaInvalida(
                    "A comparação precisa de linhas: um quadro vazio seria lido como nada a comparar.");
        }
        if (linhas.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A comparação não pode conter linha nula.");
        }
        linhas = List.copyOf(linhas);
    }

    /**
     * As cinco linhas em que o documento e a carga falam do mesmo campo.
     *
     * <p>Base de cálculo e valor do tributo ficam de fora <em>de propósito</em>:
     * a carga não declara nenhum dos dois, e uma linha cujo lado direito é sempre
     * vazio ensinaria a pessoa a ignorar o lado direito. Esses campos aparecem no
     * bloco do que foi declarado, onde pertencem.</p>
     */
    public static ComparacaoDeclaradoEIndicado de(
            ItemDocumento item, TratamentoIdentificado tratamento) {

        if (item == null) {
            throw new ConferenciaInvalida("Não há item a comparar.");
        }
        if (tratamento == null) {
            throw new ConferenciaInvalida("Não há tratamento identificado a comparar.");
        }

        LeituraDoCatalogo<String> cstsAdmitidos = cstsAdmitidos(tratamento);
        List<Linha> linhas = new ArrayList<>();
        linhas.add(Linha.de(
                "CST do IBS",
                item.cstIbs().map(CodigoCst::valor),
                SEM_CST_IBS,
                cstsAdmitidos));
        linhas.add(Linha.de(
                "CST da CBS",
                item.cstCbs().map(CodigoCst::valor),
                SEM_CST_CBS,
                cstsAdmitidos));
        linhas.add(Linha.de(
                "Alíquota do IBS - parcela estadual",
                item.aliquotaIbsUf().map(BigDecimal::toPlainString),
                SEM_ALIQUOTA_DECLARADA,
                percentuaisDe(tratamento, Tributo.IBS_UF)));
        linhas.add(Linha.de(
                "Alíquota do IBS - parcela municipal",
                item.aliquotaIbsMunicipal().map(BigDecimal::toPlainString),
                SEM_ALIQUOTA_DECLARADA,
                percentuaisDe(tratamento, Tributo.IBS_MUN)));
        linhas.add(Linha.de(
                "Alíquota da CBS",
                item.aliquotaCbs().map(BigDecimal::toPlainString),
                SEM_ALIQUOTA_DECLARADA,
                percentuaisDe(tratamento, Tributo.CBS)));
        return new ComparacaoDeclaradoEIndicado(linhas);
    }

    private static LeituraDoCatalogo<String> cstsAdmitidos(TratamentoIdentificado tratamento) {
        LeituraDoCatalogo<ClassificacaoDoCatalogo> classificacao = tratamento.classificacao();
        if (!classificacao.respondido()) {
            return LeituraDoCatalogo.ausente(classificacao.motivoDaAusencia().orElseThrow());
        }
        List<String> admitidos = classificacao.encontrado().get(0).cstsAdmitidos();
        if (admitidos.isEmpty()) {
            return LeituraDoCatalogo.ausente(
                    "a carga traz este cClassTrib, mas não lista nenhum CST junto dele");
        }
        return LeituraDoCatalogo.de(admitidos);
    }

    private static LeituraDoCatalogo<String> percentuaisDe(
            TratamentoIdentificado tratamento, Tributo tributo) {

        TratamentoDeTributo doTributo = tratamento.porTributo().stream()
                .filter(candidato -> candidato.tributo() == tributo)
                .findFirst()
                .orElseThrow(() -> new ConferenciaInvalida(
                        "O tratamento não trouxe o tributo %s, e o construtor deveria ter recusado."
                                .formatted(tributo)));

        LeituraDoCatalogo<AliquotaDoCatalogo> aliquotas = doTributo.aliquotas();
        if (!aliquotas.respondido()) {
            return LeituraDoCatalogo.ausente(aliquotas.motivoDaAusencia().orElseThrow());
        }
        return LeituraDoCatalogo.de(aliquotas.encontrado().stream()
                .map(aliquota -> "%s (%s)"
                        .formatted(aliquota.percentual().toPlainString(), aliquota.abrangencia()))
                .toList());
    }

    /**
     * Um campo, o que veio no documento e o que a carga indica.
     *
     * @param declarado            o que o documento trouxe, vazio se não trouxe
     * @param motivoDoNaoDeclarado por que não trouxe, obrigatório quando vazio
     * @param indicado             o que a carga diz, ou por que não diz
     */
    public record Linha(
            String campo,
            Optional<String> declarado,
            Optional<String> motivoDoNaoDeclarado,
            LeituraDoCatalogo<String> indicado) {

        public Linha {
            if (campo == null || campo.isBlank()) {
                throw new ConferenciaInvalida("A linha da comparação precisa nomear o campo.");
            }
            if (declarado == null || motivoDoNaoDeclarado == null) {
                throw new ConferenciaInvalida(
                        "Ausência se representa com Optional.empty() nos dois campos, nunca com nulo.");
            }
            if (declarado.isEmpty() == motivoDoNaoDeclarado.isEmpty()) {
                throw new ConferenciaInvalida(
                        ("A linha \"%s\" precisa ou do valor declarado, ou do motivo de ele não estar "
                                + "lá — exatamente um dos dois. Célula em branco numa comparação é "
                                + "lida como igualdade.").formatted(campo));
            }
            if (indicado == null) {
                throw new ConferenciaInvalida(
                        "A linha \"%s\" precisa do lado da carga, ainda que ele seja o motivo de estar "
                                + "vazio.".formatted(campo));
            }
        }

        static Linha de(
                String campo,
                Optional<String> declarado,
                String motivoSeAusente,
                LeituraDoCatalogo<String> indicado) {

            return new Linha(
                    campo,
                    declarado,
                    declarado.isPresent() ? Optional.empty() : Optional.of(motivoSeAusente),
                    indicado);
        }
    }

    private static final String SEM_CST_IBS = "o documento não declarou CST de IBS para este item";
    private static final String SEM_CST_CBS = "o documento não declarou CST de CBS para este item";
    private static final String SEM_ALIQUOTA_DECLARADA =
            "o documento não declarou esta alíquota para o item";
}
