package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ExplicacaoDaVerificacao;
import br.edu.tcc.auditoria.aplicacao.conferencia.PassoDaConferencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.PassoDeEvidencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.VersaoDaRegra;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;

import java.util.List;

// Representa uma linha do "por que este resultado" na tela do produto: a regra, o estado a que chegou, a versão e a explicação. A explicação é de um de três tipos, e o construtor confere que cada tipo traz só os campos dele.
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

    // Valida o passo: exige regra, nome ou motivo, estado com rótulo e explicação, versão ou motivo, e a explicação.
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

    // Método estático que converte o passo da aplicação, pondo o nome da regra por extenso.
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

    // Método auxiliar que devolve a versão registrada, ou null quando ela não foi gravada.
    private static String versaoOuNulo(VersaoDaRegra versao) {
        return switch (versao) {
            case VersaoDaRegra.Registrada registrada -> registrada.valor();
            case VersaoDaRegra.NaoRegistrada ausente -> null;
        };
    }

    // Método auxiliar que devolve o motivo de a versão não estar gravada, ou null quando ela está.
    private static String motivoDaVersao(VersaoDaRegra versao) {
        return switch (versao) {
            case VersaoDaRegra.Registrada registrada -> null;
            case VersaoDaRegra.NaoRegistrada ausente -> ausente.motivo();
        };
    }

    // Representa a explicação de um passo, de um dos três tipos: apontamento, pendência ou conforme calculado. Cada tipo usa só os seus campos.
    public record ExplicacaoExposta(
            String tipo,
            List<EvidenciaExposta> evidencias,
            TratamentoExposto.ReferenciaExposta fundamentacao,
            String valorEmRisco,
            String motivoDoValorAusente,
            String texto) {

        // Os três tipos de explicação.
        static final String APONTAMENTO = "APONTAMENTO";
        static final String PENDENCIA = "PENDENCIA";
        static final String DERIVACAO = "DERIVACAO";

        // Valida que a lista de evidências exista e que os campos batam com o tipo.
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

        // Método estático que converte a explicação da aplicação, de acordo com o tipo.
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

        // Método auxiliar que devolve o valor em risco como texto, ou null quando não dá para calcular.
        private static String quantiaOuNulo(ValorEmRisco valor) {
            return valor.valor().map(java.math.BigDecimal::toPlainString).orElse(null);
        }

        // Método auxiliar que exige o que um apontamento precisa: evidência, fundamento e valor ou motivo, sem texto avulso.
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

        // Método auxiliar que exige que pendência e conforme calculado tragam só o texto, sem evidência, fundamento nem valor.
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

    // Representa o que a regra olhou e o que encontrou; valor que falta vem null com o motivo.
    public record EvidenciaExposta(
            String campoAnalisado,
            String valorEncontrado,
            String motivoDoEncontradoAusente,
            String valorEsperado,
            String motivoDoEsperadoAusente,
            String origem) {

        // Valida que haja campo analisado e origem, e que os valores encontrado e esperado venham com valor ou motivo.
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

        // Método estático que converte a evidência da aplicação, pondo o motivo onde falta valor.
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
