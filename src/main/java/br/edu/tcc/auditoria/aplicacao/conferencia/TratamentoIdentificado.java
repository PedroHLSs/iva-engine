package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Representa o tratamento que a base normativa indica para um produto, na carga da análise e na data do documento; as parcelas do IBS não são somadas.
public record TratamentoIdentificado(
        String versaoDoCatalogo,
        LocalDate dataDeReferencia,
        LeituraDoCatalogo<DescricaoDeNcm> descricaoDoNcm,
        LeituraDoCatalogo<EnquadramentoDoNcm> enquadramentos,
        LeituraDoCatalogo<ClassificacaoDoCatalogo> classificacao,
        List<TratamentoDeTributo> porTributo) {

    // Valida que o tratamento tenha versão, data, os três blocos e os três tributos na ordem.
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

    // Método estático para quando não dá para determinar nada, repetindo o motivo em cada bloco.
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

    // Indica se algum dos blocos trouxe conteúdo do catálogo.
    public boolean algoFoiDeterminado() {
        return descricaoDoNcm.respondido()
                || enquadramentos.respondido()
                || classificacao.respondido()
                || porTributo.stream().anyMatch(item -> item.aliquotas().respondido());
    }

    // Método auxiliar que exige um item para cada tributo, na ordem em que o domínio os declara.
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
