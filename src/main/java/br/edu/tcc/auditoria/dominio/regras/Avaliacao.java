package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.excecao.AvaliacaoInvalida;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * O que uma regra concluiu sobre um item, e por quê.
 *
 * <p>Tipo selado em três variantes, uma para cada {@link ResultadoAvaliacao}.
 * A forma selada existe para tornar impossível o estado que este projeto mais
 * teme: uma avaliação que diz {@code NAO_AVALIADO} sem dizer o motivo, ou que
 * diz {@code ACHADO} sem trazer o apontamento. Cada variante carrega
 * obrigatoriamente o que aquele desfecho exige, e o compilador cobra.</p>
 *
 * <p><strong>Ausência de dado e conformidade não podem se parecer na saída.</strong>
 * {@link Conforme} não tem campo de texto; {@link NaoAvaliada} tem um motivo
 * obrigatório e não vazio. Quem lê o relatório distingue as duas sem inspecionar
 * nada além do tipo, e uma regra que não conseguiu julgar não tem como se
 * disfarçar de regra que julgou e nada encontrou.</p>
 *
 * <h2>Toda avaliação se identifica</h2>
 *
 * <p>As três variantes carregam regra, versão da regra, documento e item. Um
 * {@code NAO_AVALIADO} solto — só com o motivo — não diria a que item se refere,
 * e um relatório de auditoria não pode ter linha sem endereço. Em
 * {@link ComAchado} esses dados não são repetidos: são lidos do próprio
 * {@link Achado}, que já os exige.</p>
 */
public sealed interface Avaliacao {

    /** Regra que produziu esta avaliação. */
    String regraId();

    /** Versão da regra, para que o relatório seja reproduzível. */
    String regraVersao();

    /** Documento avaliado. */
    ChaveAcesso chaveAcesso();

    /** Item avaliado; vazio se a avaliação for do documento inteiro. */
    OptionalInt numeroItem();

    /** Desfecho, nos termos de {@link ResultadoAvaliacao}. */
    ResultadoAvaliacao resultado();

    /** O apontamento, quando houve; vazio nos demais desfechos. */
    Optional<Achado> achado();

    /** Por que a regra não pôde ser aplicada; vazio nos demais desfechos. */
    Optional<String> motivoDaNaoAvaliacao();

    /** Avaliação que encontrou incoerência. */
    static Avaliacao comAchado(Achado apontamento) {
        return new ComAchado(apontamento);
    }

    /** Avaliação aplicada por inteiro, sem incoerência encontrada. */
    static Avaliacao conforme(
            String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem) {
        return new Conforme(regraId, regraVersao, chaveAcesso, numeroItem);
    }

    /** Avaliação que não pôde ser concluída, com o motivo registrado. */
    static Avaliacao naoAvaliada(
            String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem, String motivo) {
        return new NaoAvaliada(regraId, regraVersao, chaveAcesso, numeroItem, motivo);
    }

    /**
     * A regra encontrou incoerência.
     *
     * @param apontamento o achado, que já carrega regra, versão, documento e item
     */
    record ComAchado(Achado apontamento) implements Avaliacao {

        public ComAchado {
            if (apontamento == null) {
                throw new AvaliacaoInvalida(
                        "Uma avaliação com achado precisa do achado. "
                                + "Para regra que nada encontrou use Avaliacao.conforme(...), e para regra "
                                + "que não pôde julgar use Avaliacao.naoAvaliada(..., motivo).");
            }
        }

        @Override
        public String regraId() {
            return apontamento.regraId();
        }

        @Override
        public String regraVersao() {
            return apontamento.regraVersao();
        }

        @Override
        public ChaveAcesso chaveAcesso() {
            return apontamento.chaveAcesso();
        }

        @Override
        public OptionalInt numeroItem() {
            return apontamento.numeroItem();
        }

        @Override
        public ResultadoAvaliacao resultado() {
            return ResultadoAvaliacao.ACHADO;
        }

        @Override
        public Optional<Achado> achado() {
            return Optional.of(apontamento);
        }

        @Override
        public Optional<String> motivoDaNaoAvaliacao() {
            return Optional.empty();
        }
    }

    /** A regra foi aplicada por inteiro e não encontrou incoerência. */
    record Conforme(String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem)
            implements Avaliacao {

        public Conforme {
            ValidacaoDeAvaliacao.exigirIdentificacao(regraId, regraVersao, chaveAcesso, numeroItem);
        }

        @Override
        public ResultadoAvaliacao resultado() {
            return ResultadoAvaliacao.CONFORME;
        }

        @Override
        public Optional<Achado> achado() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaNaoAvaliacao() {
            return Optional.empty();
        }
    }

    /**
     * A regra não pôde ser aplicada.
     *
     * <p>Faltou campo no item, faltou linha no catálogo, ou a tabela de
     * referência não alcança a data do documento. O motivo é obrigatório: sem
     * ele o relatório traria uma lacuna que ninguém conseguiria interpretar, e a
     * tentação seria lê-la como conformidade.</p>
     *
     * @param motivo por que a regra não concluiu, em texto legível por pessoa
     */
    record NaoAvaliada(
            String regraId,
            String regraVersao,
            ChaveAcesso chaveAcesso,
            OptionalInt numeroItem,
            String motivo) implements Avaliacao {

        public NaoAvaliada {
            ValidacaoDeAvaliacao.exigirIdentificacao(regraId, regraVersao, chaveAcesso, numeroItem);
            if (motivo == null || motivo.isBlank()) {
                throw new AvaliacaoInvalida(
                        "Uma avaliação não concluída precisa registrar o motivo: ausência de dado e "
                                + "conformidade não podem se parecer na saída.");
            }
        }

        @Override
        public ResultadoAvaliacao resultado() {
            return ResultadoAvaliacao.NAO_AVALIADO;
        }

        @Override
        public Optional<Achado> achado() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaNaoAvaliacao() {
            return Optional.of(motivo);
        }
    }

}
