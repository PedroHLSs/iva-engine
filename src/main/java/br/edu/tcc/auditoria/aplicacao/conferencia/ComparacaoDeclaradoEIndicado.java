package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa o que o documento declarou ao lado do que a base normativa indica, campo a campo, sem emitir veredito próprio.
public record ComparacaoDeclaradoEIndicado(List<Linha> linhas) {

    // Texto que a tela exibe para lembrar que quem julga são as regras, e não este quadro.
    public static final String QUEM_JULGA =
            "A conferência das regras sobre este produto está na situação acima e nos passos abaixo. "
                    + "Este quadro só põe lado a lado o que o documento declarou e o que a base "
                    + "normativa carregada indica.";

    // Valida que a comparação tenha linhas e que nenhuma delas seja nula.
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

    // Método estático que monta as cinco linhas comparáveis; base de cálculo e valor ficam de fora porque a carga não os declara.
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

    // Método auxiliar que busca os CSTs que a carga admite para o cClassTrib, ou o motivo de não haver.
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

    // Método auxiliar que busca os percentuais da carga para um tributo, ou o motivo de não haver.
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

    // Representa uma linha da comparação: o campo, o que veio no documento e o que a carga indica.
    public record Linha(
            String campo,
            Optional<String> declarado,
            Optional<String> motivoDoNaoDeclarado,
            LeituraDoCatalogo<String> indicado) {

        // Valida que a linha tenha o valor declarado ou o motivo da ausência, exatamente um dos dois.
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

        // Método estático que cria a linha, preenchendo o motivo quando o documento não declarou o campo.
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
