package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * O tratamento que a base normativa indica para um produto, resolvido na carga
 * daquela análise e na data daquele documento.
 *
 * <h2>As duas coordenadas andam juntas</h2>
 *
 * <p>{@code versaoDoCatalogo} é a carga que a execução registrou;
 * {@code dataDeReferencia} é a emissão do documento. Uma sem a outra não
 * identifica nada: a mesma data resolve diferente em duas cargas, e a mesma carga
 * resolve diferente em duas datas. Os dois campos vão na resposta porque a tela
 * precisa dizê-los, não porque sejam decoração de rodapé.</p>
 *
 * <h2>IBS e CBS são blocos separados, e as parcelas do IBS não se somam</h2>
 *
 * <p>A tela apresenta CBS e IBS em quadros próprios porque podem divergir: um
 * produto pode estar coerente num e não no outro, e uma linha única esconderia
 * exatamente o caso que interessa conferir.</p>
 *
 * <p>Dentro do bloco do IBS, as parcelas estadual e municipal aparecem como duas
 * linhas, cada uma com a própria vigência e a própria fonte. <strong>O sistema
 * não as soma.</strong> Somar produziria um percentual que nenhuma linha da carga
 * declara — número inventado, ainda que por aritmética —, e é justamente o tipo
 * de afirmação que este projeto não faz. Quem precisar do total soma olhando as
 * duas parcelas, sabendo de onde cada uma veio.</p>
 *
 * <h2>Os três tributos aparecem sempre</h2>
 *
 * <p>{@code porTributo} traz um item para cada constante de {@link Tributo}, na
 * ordem em que o domínio as declara, mesmo quando a carga não alcança nenhuma
 * delas — nesse caso o item carrega o motivo. Omitir o tributo que o catálogo não
 * cobre faria a tela parecer completa estando muda, que é a forma mais cara de
 * erro nesta ferramenta.</p>
 */
public record TratamentoIdentificado(
        String versaoDoCatalogo,
        LocalDate dataDeReferencia,
        LeituraDoCatalogo<DescricaoDeNcm> descricaoDoNcm,
        LeituraDoCatalogo<EnquadramentoDoNcm> enquadramentos,
        LeituraDoCatalogo<ClassificacaoDoCatalogo> classificacao,
        List<TratamentoDeTributo> porTributo) {

    public TratamentoIdentificado {
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new ConferenciaInvalida(
                    "O tratamento precisa dizer de qual carga de catálogo ele foi lido.");
        }
        if (dataDeReferencia == null) {
            throw new ConferenciaInvalida(
                    "O tratamento precisa da data em que foi resolvido: é a emissão do documento, e sem "
                            + "ela a fundamentação não é conferível.");
        }
        if (descricaoDoNcm == null || enquadramentos == null || classificacao == null) {
            throw new ConferenciaInvalida(
                    "Cada bloco do tratamento precisa existir, ainda que carregando o motivo de estar "
                            + "vazio.");
        }
        porTributo = exigirOsTresTributos(porTributo);
    }

    /**
     * O caso em que não dá para determinar nada, com o motivo repetido em cada
     * bloco.
     *
     * <p>A forma da resposta é a mesma do caso determinado, de propósito: a tela
     * percorre os mesmos campos e escreve o motivo onde escreveria o conteúdo, em
     * vez de ter um desenho especial para o fracasso — desenho especial é o que
     * costuma ser esquecido.</p>
     */
    public static TratamentoIdentificado naoDeterminado(
            String versaoDoCatalogo, LocalDate dataDeReferencia, String motivo) {

        List<TratamentoDeTributo> porTributo = new ArrayList<>();
        for (Tributo tributo : Tributo.values()) {
            porTributo.add(TratamentoDeTributo.de(tributo, LeituraDoCatalogo.ausente(motivo)));
        }
        return new TratamentoIdentificado(
                versaoDoCatalogo,
                dataDeReferencia,
                LeituraDoCatalogo.ausente(motivo),
                LeituraDoCatalogo.ausente(motivo),
                LeituraDoCatalogo.ausente(motivo),
                porTributo);
    }

    /** Se algum dos blocos trouxe conteúdo do catálogo. */
    public boolean algoFoiDeterminado() {
        return descricaoDoNcm.respondido()
                || enquadramentos.respondido()
                || classificacao.respondido()
                || porTributo.stream().anyMatch(item -> item.aliquotas().respondido());
    }

    private static List<TratamentoDeTributo> exigirOsTresTributos(List<TratamentoDeTributo> porTributo) {
        if (porTributo == null) {
            throw new ConferenciaInvalida(
                    "O tratamento precisa da lista por tributo, com um item para cada um deles.");
        }
        List<Tributo> esperados = List.of(Tributo.values());
        List<Tributo> vieram = porTributo.stream()
                .map(item -> item == null ? null : item.tributo())
                .toList();
        if (!esperados.equals(vieram)) {
            throw new ConferenciaInvalida(
                    ("O tratamento precisa trazer os tributos %s, nesta ordem, inclusive os que a carga "
                            + "não alcança. Vieram %s. Tributo omitido é lido como tributo que não "
                            + "incide.").formatted(esperados, vieram));
        }
        return List.copyOf(porTributo);
    }
}
