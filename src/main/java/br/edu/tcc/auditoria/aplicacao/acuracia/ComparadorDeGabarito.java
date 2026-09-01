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

/**
 * Confronta o que o motor concluiu com o que o gabarito afirma.
 *
 * <p>É o coração do harness, e é deliberadamente uma função pura: recebe duas
 * listas, devolve contagens. Não lê arquivo, não roda motor e não escreve nada.
 * Assim as métricas podem ser conferidas à mão, com listas montadas em teste, e
 * um erro de aritmética não se esconde atrás de leitura de XML.</p>
 *
 * <h2>O gabarito manda no que é medido</h2>
 *
 * <p>A varredura é sobre as linhas do gabarito, nunca sobre as avaliações. Uma
 * avaliação que o gabarito não rotula não entra em métrica nenhuma — ninguém
 * disse qual era a resposta certa para ela —, e só é contada em
 * {@link RelatorioDeAcuracia#avaliacoesSemLinhaNoGabarito()}, para que quem lê
 * saiba que parte do acervo ficou fora da medição.</p>
 *
 * <h2>Regra citada que não existe é recusa</h2>
 *
 * <p>Um {@code regra_id} digitado errado no gabarito viraria, em silêncio,
 * dezenas de {@code SEM_AVALIACAO} — e quem lesse o relatório concluiria que o
 * acervo está desalinhado, quando o que há é um erro de digitação. A comparação
 * falha, listando as regras que o conjunto conhece.</p>
 */
public final class ComparadorDeGabarito {

    /**
     * Compara e monta o relatório.
     *
     * @param gabarito                 a verdade de referência
     * @param avaliacoes               tudo o que o motor produziu, sem filtro
     * @param regrasDoConjunto         identificadores na ordem de aplicação; cada
     *                                 uma delas ganha linha no relatório
     * @param versaoDoCatalogo         carga de catálogo da rodada
     * @param versaoDoConjuntoDeRegras versão do conjunto da rodada
     * @param documentosAuditados      documentos lidos da origem
     * @param itensAuditados           itens somados de todos os documentos
     */
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

        Map<EnderecoDaAvaliacao, ResultadoAvaliacao> porEndereco = indexar(avaliacoes);

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

    /**
     * Indexa as avaliações do motor por endereço.
     *
     * <p>Duas avaliações no mesmo endereço seriam a mesma regra concluindo duas
     * coisas sobre o mesmo item. O motor não faz isso — percorre item por item,
     * regra por regra —, mas se algum dia fizer, a medição não pode escolher uma
     * delas em silêncio: seria o gabarito medindo um sorteio.</p>
     */
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

    private static int contarSemLinhaNoGabarito(List<Avaliacao> avaliacoes, Gabarito gabarito) {
        return (int) avaliacoes.stream()
                .filter(avaliacao -> EnderecoDaAvaliacao.de(avaliacao)
                        .map(endereco -> !gabarito.contem(endereco))
                        .orElse(true))
                .count();
    }

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

    private static void exigir(Object valor, String oQueFalta) {
        if (valor == null) {
            throw new AvaliacaoDeAcuraciaInvalida("A comparação precisa de %s.".formatted(oQueFalta));
        }
    }
}
