package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Avaliações e linhas de gabarito para os testes de medição de acurácia.
 *
 * <p><strong>Todos os valores são deliberadamente fictícios e nenhum deles deve
 * ser lido como afirmação sobre a legislação.</strong> Chaves de um dígito
 * repetido, identificadores de regra "RX1" e "RX2", vigência em 1900,
 * fundamento com a palavra "fictício": nada disso existe na norma.</p>
 */
final class CenarioDeAcuracia {

    static final String CHAVE = "1".repeat(44);
    static final String OUTRA_CHAVE = "2".repeat(44);

    static final String REGRA_PRIMEIRA = "RX1";
    static final String REGRA_SEGUNDA = "RX2";
    static final List<String> CONJUNTO = List.of(REGRA_PRIMEIRA, REGRA_SEGUNDA);

    static final String VERSAO_REGRA = "0.0.0-ficticia";
    static final String VERSAO_CATALOGO = "catalogo-ficticio-0";
    static final String VERSAO_CONJUNTO = "conjunto-ficticio-0";
    static final String FUNDAMENTO = "FUNDAMENTO FICTICIO PARA TESTE";

    private CenarioDeAcuracia() {
    }

    static ChaveAcesso chave(String valor) {
        return new ChaveAcesso(valor);
    }

    static EnderecoDaAvaliacao endereco(String chave, int numeroItem, String regraId) {
        return new EnderecoDaAvaliacao(chave(chave), numeroItem, regraId);
    }

    /** Avaliação do motor com o desfecho indicado, no endereço indicado. */
    static Avaliacao avaliacao(
            String chave, int numeroItem, String regraId, ResultadoAvaliacao resultado) {

        return switch (resultado) {
            case ACHADO -> Avaliacao.comAchado(achado(chave, numeroItem, regraId));
            case CONFORME -> Avaliacao.conforme(
                    regraId, VERSAO_REGRA, chave(chave), OptionalInt.of(numeroItem));
            case NAO_AVALIADO -> Avaliacao.naoAvaliada(
                    regraId,
                    VERSAO_REGRA,
                    chave(chave),
                    OptionalInt.of(numeroItem),
                    "motivo fictício: faltou dado no documento");
        };
    }

    /** Avaliação de documento inteiro, sem item — o motor atual não produz nenhuma. */
    static Avaliacao avaliacaoDeDocumento(String chave, String regraId) {
        return Avaliacao.conforme(regraId, VERSAO_REGRA, chave(chave), OptionalInt.empty());
    }

    /** Linha de gabarito no endereço indicado, com o rótulo indicado. */
    static LinhaDeGabarito linha(
            int numeroDaLinha, String chave, int numeroItem, String regraId, RotuloEsperado rotulo) {

        return new LinhaDeGabarito(numeroDaLinha, endereco(chave, numeroItem, regraId), rotulo);
    }

    static Achado achado(String chave, int numeroItem, String regraId) {
        return new Achado(
                regraId,
                VERSAO_REGRA,
                Severidade.GRAVE,
                chave(chave),
                OptionalInt.of(numeroItem),
                List.of(evidencia()),
                FUNDAMENTO,
                PeriodoVigencia.de(LocalDate.of(1900, 1, 1), LocalDate.of(1900, 12, 31)),
                ValorEmRisco.calculado(new BigDecimal("99.99")));
    }

    private static Evidencia evidencia() {
        return new Evidencia(
                "campoFicticio",
                Optional.of("99,99"),
                Optional.of("00,00"),
                new OrigemEvidencia.DaRegra("derivação fictícia para teste"));
    }
}
