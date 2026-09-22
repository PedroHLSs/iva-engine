package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ExplicacaoDaVerificacao;
import br.edu.tcc.auditoria.aplicacao.conferencia.PassoDaConferencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.PassoDeEvidencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.VersaoDaRegra;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;

import java.util.List;

/**
 * Uma linha do "por que este resultado", como a tela a recebe.
 *
 * <h2>A explicação tem tipo, e o tipo manda no que vem junto</h2>
 *
 * <p>São três procedências — apontamento gravado, pendência gravada e conforme
 * derivado — e cada uma traz campos diferentes. O construtor confere essa
 * coerência: um passo que se diz apontamento e chega sem evidência, ou um que se
 * diz derivação e chega com fundamento normativo, é recusado aqui em vez de
 * chegar pela metade na tela.</p>
 *
 * <p>É o mesmo cuidado do par "nulo com o campo irmão dizendo por quê" da D009,
 * aplicado a uma estrutura com três formas em vez de duas.</p>
 *
 * <p>{@code regraNome} e {@code motivoDoNomeDaRegraAusente} foram acrescentados
 * depois da Etapa 11: o cabeçalho do passo escrevia só o código, e "R06" não diz
 * a quem confere o produto que pergunta foi feita. Ver {@link NomeDaRegra}.</p>
 */
public record PassoExposto(
        String regraId,
        String regraNome,
        String motivoDoNomeDaRegraAusente,
        String estado,
        String rotuloDoEstado,
        String explicacaoDoEstado,
        String regraVersao,
        String motivoDaVersaoAusente,
        ExplicacaoExposta explicacao) {

    public PassoExposto {
        if (regraId == null || regraId.isBlank()) {
            throw new RespostaInvalida("O passo precisa dizer de que regra ele é.");
        }
        NomeDaRegra.exigirPar(regraNome, motivoDoNomeDaRegraAusente, regraId);
        if (estado == null || estado.isBlank()
                || rotuloDoEstado == null || rotuloDoEstado.isBlank()
                || explicacaoDoEstado == null || explicacaoDoEstado.isBlank()) {
            throw new RespostaInvalida(
                    ("O passo da regra %s precisa do estado com rótulo e explicação: código sozinho faz "
                            + "quem consome escrever a frase por conta própria.").formatted(regraId));
        }
        if ((regraVersao == null) == (motivoDaVersaoAusente == null)) {
            throw new RespostaInvalida(
                    ("A versão da regra %s precisa ou estar presente, ou vir nula com o motivo. O banco "
                            + "não grava avaliação conforme, e por isso esse motivo existe.")
                            .formatted(regraId));
        }
        if (explicacao == null) {
            throw new RespostaInvalida(
                    ("O passo da regra %s precisa da explicação. Um passo sem razão é a linha que a "
                            + "pessoa lê e continua sem saber o que aconteceu.").formatted(regraId));
        }
    }

    static PassoExposto de(PassoDaConferencia passo) {
        NomeDaRegra nome = NomeDaRegra.de(passo.regraId());
        return new PassoExposto(
                passo.regraId(),
                nome.nome(),
                nome.motivoDaAusencia(),
                passo.estado().name(),
                passo.estado().rotulo(),
                passo.estado().explicacao(),
                versaoOuNulo(passo.versao()),
                motivoDaVersao(passo.versao()),
                ExplicacaoExposta.de(passo.explicacao()));
    }

    private static String versaoOuNulo(VersaoDaRegra versao) {
        return switch (versao) {
            case VersaoDaRegra.Registrada registrada -> registrada.valor();
            case VersaoDaRegra.NaoRegistrada ausente -> null;
        };
    }

    private static String motivoDaVersao(VersaoDaRegra versao) {
        return switch (versao) {
            case VersaoDaRegra.Registrada registrada -> null;
            case VersaoDaRegra.NaoRegistrada ausente -> ausente.motivo();
        };
    }

    /** Os três tipos de explicação, com os campos que cada um usa. */
    public record ExplicacaoExposta(
            String tipo,
            List<EvidenciaExposta> evidencias,
            TratamentoExposto.ReferenciaExposta fundamentacao,
            String valorEmRisco,
            String motivoDoValorAusente,
            String texto) {

        static final String APONTAMENTO = "APONTAMENTO";
        static final String PENDENCIA = "PENDENCIA";
        static final String DERIVACAO = "DERIVACAO";

        public ExplicacaoExposta {
            if (evidencias == null) {
                throw new RespostaInvalida(
                        "A lista de evidências deve ser vazia fora do apontamento, nunca nula.");
            }
            switch (tipo == null ? "" : tipo) {
                case APONTAMENTO -> exigirApontamento(
                        evidencias, fundamentacao, valorEmRisco, motivoDoValorAusente, texto);
                case PENDENCIA, DERIVACAO -> exigirSemApontamento(
                        evidencias, fundamentacao, valorEmRisco, motivoDoValorAusente, texto, tipo);
                default -> throw new RespostaInvalida(
                        ("Tipo de explicação desconhecido: \"%s\". São três, e cada um traz campos "
                                + "diferentes.").formatted(tipo));
            }
            evidencias = List.copyOf(evidencias);
        }

        static ExplicacaoExposta de(ExplicacaoDaVerificacao explicacao) {
            return switch (explicacao) {
                case ExplicacaoDaVerificacao.PorApontamento apontamento -> new ExplicacaoExposta(
                        APONTAMENTO,
                        apontamento.evidencias().stream().map(EvidenciaExposta::de).toList(),
                        TratamentoExposto.ReferenciaExposta.de(apontamento.fundamentacao()),
                        quantiaOuNulo(apontamento.valorEmRisco()),
                        apontamento.valorEmRisco().motivoDaAusencia().orElse(null),
                        null);
                case ExplicacaoDaVerificacao.PorPendencia pendencia -> new ExplicacaoExposta(
                        PENDENCIA, List.of(), null, null, null, pendencia.motivo());
                case ExplicacaoDaVerificacao.PorDerivacao derivacao -> new ExplicacaoExposta(
                        DERIVACAO, List.of(), null, null, null, derivacao.conta());
            };
        }

        private static String quantiaOuNulo(ValorEmRisco valor) {
            return valor.valor().map(java.math.BigDecimal::toPlainString).orElse(null);
        }

        private static void exigirApontamento(
                List<EvidenciaExposta> evidencias,
                TratamentoExposto.ReferenciaExposta fundamentacao,
                String valorEmRisco,
                String motivoDoValorAusente,
                String texto) {

            if (evidencias.isEmpty()) {
                throw new RespostaInvalida(
                        "Apontamento sem evidência não é conferível, e o domínio não permite criar um.");
            }
            if (fundamentacao == null) {
                throw new RespostaInvalida(
                        "O apontamento precisa do fundamento e da vigência aplicada.");
            }
            if ((valorEmRisco == null) == (motivoDoValorAusente == null)) {
                throw new RespostaInvalida(
                        "O valor em risco precisa ou estar calculado, ou vir nulo com o motivo de não "
                                + "ser calculável. Zero no lugar do motivo seria valor inventado.");
            }
            if (texto != null) {
                throw new RespostaInvalida(
                        "O apontamento se explica pelas evidências, não por um texto avulso.");
            }
        }

        private static void exigirSemApontamento(
                List<EvidenciaExposta> evidencias,
                TratamentoExposto.ReferenciaExposta fundamentacao,
                String valorEmRisco,
                String motivoDoValorAusente,
                String texto,
                String tipo) {

            if (!evidencias.isEmpty() || fundamentacao != null
                    || valorEmRisco != null || motivoDoValorAusente != null) {
                throw new RespostaInvalida(
                        ("Uma explicação do tipo %s não tem evidência, fundamento nem valor em risco: "
                                + "nada disso foi gravado para ela.").formatted(tipo));
            }
            if (texto == null || texto.isBlank()) {
                throw new RespostaInvalida(
                        ("Uma explicação do tipo %s é o texto dela. Vazio aqui seria a tela dizendo que "
                                + "algo aconteceu sem dizer o quê.").formatted(tipo));
            }
        }
    }

    /** O que a regra olhou e o que encontrou. */
    public record EvidenciaExposta(
            String campoAnalisado,
            String valorEncontrado,
            String motivoDoEncontradoAusente,
            String valorEsperado,
            String motivoDoEsperadoAusente,
            String origem) {

        public EvidenciaExposta {
            if (campoAnalisado == null || campoAnalisado.isBlank()) {
                throw new RespostaInvalida("A evidência precisa dizer qual campo foi examinado.");
            }
            if ((valorEncontrado == null) == (motivoDoEncontradoAusente == null)) {
                throw new RespostaInvalida(
                        ("O valor encontrado em \"%s\" precisa ou estar lá, ou vir nulo dizendo que o "
                                + "documento não trouxe o campo. Traço em branco apagaria justamente o "
                                + "motivo do apontamento.").formatted(campoAnalisado));
            }
            if ((valorEsperado == null) == (motivoDoEsperadoAusente == null)) {
                throw new RespostaInvalida(
                        ("O valor esperado em \"%s\" precisa ou estar lá, ou vir nulo dizendo que a "
                                + "regra não tinha referência a opor. São coisas diferentes.")
                                .formatted(campoAnalisado));
            }
            if (origem == null || origem.isBlank()) {
                throw new RespostaInvalida(
                        "A evidência precisa dizer de onde o valor saiu.");
            }
        }

        static EvidenciaExposta de(PassoDeEvidencia evidencia) {
            return new EvidenciaExposta(
                    evidencia.campoAnalisado(),
                    evidencia.valorEncontrado().orElse(null),
                    evidencia.valorEncontrado().isPresent()
                            ? null : PassoDeEvidencia.NAO_VEIO_NO_DOCUMENTO,
                    evidencia.valorEsperado().orElse(null),
                    evidencia.valorEsperado().isPresent()
                            ? null : PassoDeEvidencia.SEM_REFERENCIA_A_OPOR,
                    evidencia.origem());
        }
    }
}
