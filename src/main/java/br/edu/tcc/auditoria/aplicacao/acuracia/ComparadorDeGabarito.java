package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;
import br.edu.tcc.auditoria.dominio.acuracia.Desfecho;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

//Classe de comparação entre o gabarito rotulado a mão e as avaliações do motor de regras, gerando o relatorio de acurácia.
public final class ComparadorDeGabarito {

    public RelatorioDeAcuracia comparar(
            Gabarito gabarito,
            List<Avaliacao> avaliacoes,
            List<String> regrasDoConjunto,
            String versaoDoCatalogo,
            String versaoDoConjuntoDeRegras,
            int documentosAuditados,
            int itensAuditados) {

        exigir(gabarito, "o gabarito rotulado");
        exigir(avaliacoes, "a lista de avaliações do motor");
        exigir(regrasDoConjunto, "os identificadores das regras do conjunto");
        if (avaliacoes.stream().anyMatch(Objects::isNull)) {
            throw new AvaliacaoDeAcuraciaInvalida("A lista de avaliações não pode conter elemento nulo.");
        }
        exigirRegrasConhecidas(gabarito, regrasDoConjunto);

        // Garante a não duplicidade para que não haja aleatoriedade na medição
        Map<EnderecoDaAvaliacao, ResultadoAvaliacao> porEndereco = indexar(avaliacoes);

        // Agrupa os desfechos por regra, para depois contar acertos e erros
        Map<String, List<Desfecho>> desfechosPorRegra = new LinkedHashMap<>();
        regrasDoConjunto.forEach(regraId -> desfechosPorRegra.put(regraId, new ArrayList<>()));
        List<EnderecoDaAvaliacao> semAvaliacao = new ArrayList<>();

        for (LinhaDeGabarito linha : gabarito.linhas()) {
            Optional<ResultadoAvaliacao> obtido = Optional.ofNullable(porEndereco.get(linha.endereco()));
            Desfecho desfecho = Desfecho.de(linha.rotulo(), obtido);

            desfechosPorRegra.get(linha.regraId()).add(desfecho);
            if (desfecho == Desfecho.SEM_AVALIACAO) {
                semAvaliacao.add(linha.endereco());
            }
        }

        List<MetricasDaRegra> porRegra = regrasDoConjunto.stream()
                .map(regraId -> new MetricasDaRegra(
                        regraId, ContagemDeAcuracia.contar(desfechosPorRegra.get(regraId))))
                .toList();

        return new RelatorioDeAcuracia(
                versaoDoCatalogo,
                versaoDoConjuntoDeRegras,
                documentosAuditados,
                itensAuditados,
                avaliacoes.size(),
                contarSemLinhaNoGabarito(avaliacoes, gabarito),
                porRegra,
                semAvaliacao);
    }
    // Garantir que não haja duplicidade de endereços nas avaliações do motor, para que não haja aleatoriedade na medição.
    private static Map<EnderecoDaAvaliacao, ResultadoAvaliacao> indexar(List<Avaliacao> avaliacoes) {
        Map<EnderecoDaAvaliacao, ResultadoAvaliacao> indice = new LinkedHashMap<>();
        for (Avaliacao avaliacao : avaliacoes) {
            Optional<EnderecoDaAvaliacao> endereco = EnderecoDaAvaliacao.de(avaliacao);
            if (endereco.isEmpty()) {
                continue;
            }
            ResultadoAvaliacao anterior = indice.put(endereco.get(), avaliacao.resultado());
            if (anterior != null && anterior != avaliacao.resultado()) {
                throw new AvaliacaoDeAcuraciaInvalida(
                        ("O motor produziu dois desfechos diferentes para o item %d do documento %s na "
                                + "regra %s: %s e %s. Não há como medir contra um gabarito que rotula "
                                + "esse endereço uma vez só.")
                                .formatted(
                                        endereco.get().numeroItem(),
                                        endereco.get().chaveAcesso().valor(),
                                        endereco.get().regraId(),
                                        anterior,
                                        avaliacao.resultado()));
            }
        }
        return indice;
    }

    // Conta quantas avaliações não corresponde ao gabarito
    private static int contarSemLinhaNoGabarito(List<Avaliacao> avaliacoes, Gabarito gabarito) {
        return (int) avaliacoes.stream()
                .filter(avaliacao -> EnderecoDaAvaliacao.de(avaliacao)
                        .map(endereco -> !gabarito.contem(endereco))
                        .orElse(true))
                .count();
    }
    // Garante que o gabarito não cite regras que não estão no conjunto de regras, para evitar erros de digitação.
    private static void exigirRegrasConhecidas(Gabarito gabarito, List<String> regrasDoConjunto) {
        Set<String> desconhecidas = new TreeSet<>(gabarito.regrasCitadas());
        desconhecidas.removeAll(regrasDoConjunto);
        if (desconhecidas.isEmpty()) {
            return;
        }
        throw new AvaliacaoDeAcuraciaInvalida(
                ("O gabarito cita regra que o conjunto não tem: %s. O conjunto aplica %s. Medir assim "
                        + "transformaria um erro de digitação em linhas sem avaliação, e o relatório "
                        + "acusaria o acervo em vez do arquivo.")
                        .formatted(String.join(", ", desconhecidas), String.join(", ", regrasDoConjunto)));
    }
    // Garante que os parâmetros não sejam nulos, para evitar erros de execução.
    private static void exigir(Object valor, String oQueFalta) {
        if (valor == null) {
            throw new AvaliacaoDeAcuraciaInvalida("A comparação precisa de %s.".formatted(oQueFalta));
        }
    }
}
