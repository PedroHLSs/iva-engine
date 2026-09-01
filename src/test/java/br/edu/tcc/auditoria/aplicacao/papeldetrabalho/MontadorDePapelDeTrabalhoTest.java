package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MontadorDePapelDeTrabalhoTest {

    private static final UUID EXECUCAO = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final Instant AGORA = Instant.parse("1900-01-01T00:00:00Z");
    private static final String CHAVE = "1".repeat(44);
    private static final String OUTRA_CHAVE = "2".repeat(44);
    private static final String PSEUDONIMO = "f".repeat(64);
    private static final String HASH_DE_ITEM = "e".repeat(64);
    private static final String REGRA = "RXX";
    private static final String OUTRA_REGRA = "RYY";
    private static final String VERSAO = "0.0.0-ficticia";
    private static final String MOTIVO_REPETIDO = "Motivo ficticio repetido.";
    private static final String MOTIVO_UNICO = "Motivo ficticio unico.";

    @Test
    void deveTrocarAChaveDeAcessoPeloPseudonimo() {
        PapelDeTrabalho papel = montar(List.of(achadoAberto()), List.of());

        assertThat(papel.achados()).hasSize(1);
        assertThat(papel.achados().get(0).documentoPseudonimizado())
                .as("a chave carrega o CNPJ do emitente e não pode sair na exportação")
                .isEqualTo(PSEUDONIMO)
                .isNotEqualTo(CHAVE);
    }

    @Test
    void deveTrazerAIdentificacaoDoDocumentoQuePermiteLocalizarANota() {
        PapelDeTrabalho papel = montar(List.of(achadoAberto()), List.of());
        LinhaDeAchado linha = papel.achados().get(0);

        assertThat(linha.modelo()).isEqualTo("99");
        assertThat(linha.serie()).isEqualTo("999");
        assertThat(linha.numero()).isEqualTo("111111");
        assertThat(linha.dataEmissao()).isEqualTo(LocalDate.of(1900, 6, 15));
    }

    @Test
    void deveMarcarComoAbertoOAchadoSemTratativa() {
        PapelDeTrabalho papel = montar(List.of(achadoAberto()), List.of());

        assertThat(papel.achados().get(0).statusDeTratativa()).isEqualTo(StatusDeTratativa.ABERTO);
        assertThat(papel.achados().get(0).justificativaDaTratativa()).isEmpty();
    }

    @Test
    void deveTrazerADecisaoEAJustificativaDoAchadoTratado() {
        PapelDeTrabalho papel = montar(List.of(achadoTratado()), List.of());
        LinhaDeAchado linha = papel.achados().get(0);

        assertThat(linha.statusDeTratativa()).isEqualTo(StatusDeTratativa.REFUTADO);
        assertThat(linha.justificativaDaTratativa()).contains("Justificativa ficticia.");
        assertThat(linha.tratadoEm()).contains(AGORA);
    }

    @Test
    void devePreservarAsEvidenciasAlinhadas() {
        PapelDeTrabalho papel = montar(List.of(achadoAberto()), List.of());
        LinhaDeAchado linha = papel.achados().get(0);

        assertThat(linha.campos()).containsExactly("campoFicticio");
        assertThat(linha.valoresEncontrados()).containsExactly(Optional.of("99,99"));
        assertThat(linha.valoresEsperados())
                .as("ausência de referência continua sendo ausência, e não texto vazio")
                .containsExactly(Optional.empty());
    }

    @Test
    void deveAgruparOsMotivosDoMaisFrequenteParaOMenos() {
        PapelDeTrabalho papel = montar(List.of(), List.of(
                naoAvaliada(CHAVE, 1, REGRA, MOTIVO_UNICO),
                naoAvaliada(CHAVE, 2, OUTRA_REGRA, MOTIVO_REPETIDO),
                naoAvaliada(OUTRA_CHAVE, 1, OUTRA_REGRA, MOTIVO_REPETIDO)));

        assertThat(papel.motivosAgrupados()).hasSize(2);
        assertThat(papel.motivosAgrupados().get(0).quantidade()).isEqualTo(2);
        assertThat(papel.motivosAgrupados().get(0).motivo()).isEqualTo(MOTIVO_REPETIDO);
        assertThat(papel.motivosAgrupados().get(1).quantidade()).isEqualTo(1);
    }

    @Test
    void deveSepararMotivosIguaisDeRegrasDiferentes() {
        PapelDeTrabalho papel = montar(List.of(), List.of(
                naoAvaliada(CHAVE, 1, REGRA, MOTIVO_REPETIDO),
                naoAvaliada(CHAVE, 2, OUTRA_REGRA, MOTIVO_REPETIDO)));

        assertThat(papel.motivosAgrupados())
                .as("o mesmo texto vindo de regras diferentes aponta para causas diferentes")
                .hasSize(2)
                .allSatisfy(agrupado -> assertThat(agrupado.quantidade()).isEqualTo(1));
    }

    @Test
    void deveContarItensDistintosAtingidos() {
        PapelDeTrabalho papel = montar(List.of(), List.of(
                naoAvaliada(CHAVE, 1, REGRA, MOTIVO_UNICO),
                naoAvaliada(CHAVE, 1, OUTRA_REGRA, MOTIVO_REPETIDO),
                naoAvaliada(OUTRA_CHAVE, 1, REGRA, MOTIVO_REPETIDO)));

        assertThat(papel.quantidadeDeNaoAvaliados()).isEqualTo(3);
        assertThat(papel.itensNaoAvaliados())
                .as("duas regras sobre o mesmo item são duas avaliações, mas um item só")
                .isEqualTo(2);
    }

    @Test
    void deveRecusarAchadoDeDocumentoQueNaoEstaGravado() {
        MontadorDePapelDeTrabalho montador = new MontadorDePapelDeTrabalho(
                execucaoId -> List.of(achadoAberto()),
                execucaoId -> List.of(),
                chaves -> Map.of(),
                chave -> new IdentificadorPseudonimizado(PSEUDONIMO));

        assertThatThrownBy(() -> montador.montar(execucaoCom(1)))
                .isInstanceOf(PapelDeTrabalhoInvalido.class)
                .hasMessageNotContaining(CHAVE);
    }

    @Test
    void deveExigirExecucao() {
        MontadorDePapelDeTrabalho montador = new MontadorDePapelDeTrabalho(
                execucaoId -> List.of(),
                execucaoId -> List.of(),
                chaves -> Map.of(),
                chave -> new IdentificadorPseudonimizado(PSEUDONIMO));

        assertThatThrownBy(() -> montador.montar(null))
                .isInstanceOf(PapelDeTrabalhoInvalido.class);
    }

    private static PapelDeTrabalho montar(
            List<AchadoRegistrado> achados, List<NaoAvaliadaRegistrada> naoAvaliadas) {

        ConsultaDeAchadosDaExecucao consultaDeAchados = execucaoId -> achados;
        ConsultaDeNaoAvaliadas consultaDeNaoAvaliadas = execucaoId -> naoAvaliadas;
        ConsultaDeDocumentos consultaDeDocumentos = chaves -> documentos(chaves);

        return new MontadorDePapelDeTrabalho(
                consultaDeAchados,
                consultaDeNaoAvaliadas,
                consultaDeDocumentos,
                chave -> new IdentificadorPseudonimizado(PSEUDONIMO))
                .montar(execucaoCom(achados.size()));
    }

    private static Map<ChaveAcesso, DadosDoDocumento> documentos(Collection<ChaveAcesso> chaves) {
        return chaves.stream().collect(java.util.stream.Collectors.toMap(
                chave -> chave,
                chave -> new DadosDoDocumento(
                        chave, "99", "999", "111111", LocalDate.of(1900, 6, 15),
                        br.edu.tcc.auditoria.dominio.Uf.SP)));
    }

    private static ExecucaoAuditoria execucaoCom(int quantidadeDeAchados) {
        return ExecucaoAuditoria.de(
                EXECUCAO, AGORA, "a".repeat(64), "catalogo-ficticio", "0.0-ficticia",
                1, 1, List.of(REGRA, OUTRA_REGRA),
                java.util.Collections.nCopies(quantidadeDeAchados, achado()));
    }

    private static AchadoRegistrado achadoAberto() {
        return new AchadoRegistrado(
                UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                achado(),
                new HashDoItem(HASH_DE_ITEM),
                Optional.empty(),
                AGORA,
                AGORA);
    }

    private static AchadoRegistrado achadoTratado() {
        return new AchadoRegistrado(
                UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                achado(),
                new HashDoItem(HASH_DE_ITEM),
                Optional.of(new Tratativa(
                        new ChaveDeTratativa(new HashDoItem(HASH_DE_ITEM), REGRA, VERSAO),
                        DecisaoDeTratativa.REFUTADO,
                        "Justificativa ficticia.",
                        AGORA)),
                AGORA,
                AGORA);
    }

    private static Achado achado() {
        return new Achado(
                REGRA,
                VERSAO,
                Severidade.GRAVE,
                new ChaveAcesso(CHAVE),
                OptionalInt.of(1),
                List.of(new Evidencia(
                        "campoFicticio",
                        Optional.of("99,99"),
                        Optional.empty(),
                        new OrigemEvidencia.DaRegra("derivação fictícia para teste"))),
                "FUNDAMENTO FICTICIO PARA TESTE",
                PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1)),
                ValorEmRisco.naoCalculavel("motivo fictício de teste"));
    }

    private static NaoAvaliadaRegistrada naoAvaliada(
            String chave, int numeroItem, String regraId, String motivo) {
        return new NaoAvaliadaRegistrada(new ChaveAcesso(chave), numeroItem, regraId, VERSAO, motivo);
    }
}
