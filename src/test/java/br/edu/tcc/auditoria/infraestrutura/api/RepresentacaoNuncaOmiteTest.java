package br.edu.tcc.auditoria.infraestrutura.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ausência sem motivo não constrói.
 *
 * <p>A regra da etapa — "campo ausente é {@code null} com um campo irmão dizendo por
 * quê" — não é convenção de quem escreve o montador: está nos construtores dos DTOs,
 * e um {@code null} solto lança antes de virar resposta. É a mesma técnica que a
 * Etapa 1 usou para tornar impossível um apontamento sem evidência, e a Etapa 3 para
 * tornar impossível um "não avaliei" sem motivo.</p>
 *
 * <p>Os pares são conferidos nas duas direções: falta o motivo, recusa; sobra o
 * motivo junto do valor, recusa também. Só o segundo caso pega o defeito de alguém
 * preencher os dois "por segurança" e a resposta passar a afirmar que a chave está
 * presente e omitida ao mesmo tempo.</p>
 */
class RepresentacaoNuncaOmiteTest {

    private static final String PSEUDONIMO = "a".repeat(64);
    private static final String CHAVE = "1".repeat(44);
    private static final String MOTIVO = "Motivo ficticio de teste.";
    private static final LocalDate DATA = LocalDate.of(2026, 1, 15);

    @Test
    void deveRecusarChaveOmitidaSemMotivo() {
        assertThatThrownBy(() -> new DocumentoExposto(
                PSEUDONIMO, null, null, "55", "1", "999", DATA, "MG"))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("sem motivo");
    }

    @Test
    void deveRecusarChavePresenteEOmitidaAoMesmoTempo() {
        assertThatThrownBy(() -> new DocumentoExposto(
                PSEUDONIMO, CHAVE, MOTIVO, "55", "1", "999", DATA, "MG"))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("presente e omitida");
    }

    @Test
    void deveRecusarDocumentoSemPseudonimo() {
        assertThatThrownBy(() -> new DocumentoExposto(
                null, CHAVE, null, "55", "1", "999", DATA, "MG"))
                .as("é o único identificador que a resposta sempre tem")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("pseudônimo");
    }

    @Test
    void deveRecusarJustificativaOmitidaSemMotivo() {
        assertThatThrownBy(() -> new AchadoExposto.TratativaExposta(
                "ACEITO", Instant.EPOCH, null, null))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("sem motivo");
    }

    @Test
    void deveRecusarVigenciaSemFimESemDizerQueEAberta() {
        assertThatThrownBy(() ->
                new AchadoExposto.VigenciaExposta(DATA, null, null))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("aberta");
    }

    @Test
    void deveMarcarVigenciaAbertaComOMotivoAoInvesDeDeixarEmBranco() {
        AchadoExposto.VigenciaExposta aberta = AchadoExposto.VigenciaExposta.aberta(DATA);

        assertThat(aberta.fim()).isNull();
        assertThat(aberta.motivoDoFimAusente())
                .as("data em branco sem explicação é indistinguível de data que ninguém preencheu")
                .isNotBlank();
    }

    @Test
    void deveRecusarContagemDerivadaSemValorESemMotivo() {
        assertThatThrownBy(() -> new ContagemDerivada(null, null, null))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("indistinguível de zero");
    }

    @Test
    void deveRecusarContagemDerivadaSemMostrarAConta() {
        assertThatThrownBy(() -> new ContagemDerivada(10L, null, null))
                .as("número sem procedência numa auditoria é número que ninguém confere")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("mostrar a conta");
    }

    @Test
    void deveRecusarDerivarConformeNegativoEmVezDeReportarZero() {
        ContagemDerivada naoDerivavel = ContagemDerivada.conformesDe(10, 8, 5, "avaliacoesProduzidas");

        assertThat(naoDerivavel.valor())
                .as("zero seria uma afirmação sobre o acervo, e o problema está no banco")
                .isNull();
        assertThat(naoDerivavel.motivoDaAusencia())
                .contains("a conta não fecha")
                .contains("se contradizem");
    }

    @Test
    void deveRecusarNaoAvaliadaSemDizerOResultadoPorExtenso() {
        assertThatThrownBy(() -> new NaoAvaliadaExposta(
                new DocumentoExposto(PSEUDONIMO, null, MOTIVO, "55", "1", "999", DATA, "MG"),
                1, "R01", "Nome ficticio", null, "1.0.0", null, MOTIVO))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("NAO_AVALIADO");
    }

    @Test
    void deveRecusarNaoAvaliadaSemMotivo() {
        assertThatThrownBy(() -> new NaoAvaliadaExposta(
                new DocumentoExposto(PSEUDONIMO, null, MOTIVO, "55", "1", "999", DATA, "MG"),
                1, "R01", "Nome ficticio", null, "1.0.0", "NAO_AVALIADO", null))
                .as("é a única coisa que esta linha tem a dizer")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("precisa do motivo");
    }

    @Test
    void deveRecusarRespostaDaExecucaoComSomaPorRegraDivergindoDoTotal() {
        assertThatThrownBy(() -> new RespostaDaExecucao.PorRegra(
                "R01",
                "Nome ficticio",
                null,
                0,
                5,
                ContagemDerivada.conformesDe(10, 0, 5, "quantidadeItens"),
                List.of(new RespostaDaExecucao.MotivoDoNaoAvaliado(MOTIVO, 2))))
                .as("o resumo não pode divergir do detalhe: é a primeira coisa que quem confere confere")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("somam 2");
    }

    // -----------------------------------------------------------------------
    // Os DTOs da conferência seguem a mesma regra.
    // Acrescentados na etapa de conferência.
    // -----------------------------------------------------------------------

    @Test
    void deveRecusarBlocoDaBaseNormativaVazioSemMotivo() {
        assertThatThrownBy(() -> new LeituraExposta<String>(List.of(), null))
                .as("bloco vazio e mudo é lido como ausência de tratamento, que é outra coisa")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("não foi dito por quê");
    }

    @Test
    void deveRecusarBlocoDaBaseNormativaComConteudoEMotivoAoMesmoTempo() {
        assertThatThrownBy(() -> new LeituraExposta<>(List.of("conteudo"), MOTIVO))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("ao mesmo tempo, um motivo");
    }

    @Test
    void deveRecusarVigenciaDoCatalogoSemFimESemDizerQueEAberta() {
        assertThatThrownBy(() -> new TratamentoExposto.ReferenciaExposta(
                "FONTE FICTICIA", DATA, null, null))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("vigência encerrada em data desconhecida");
    }

    @Test
    void deveRecusarReducaoAusenteSemDizerQueACargaNaoDeclarou() {
        assertThatThrownBy(() -> new TratamentoExposto.ClassificacaoExposta(
                "FICT-001",
                List.of("999"),
                null,
                "DISPOSITIVO FICTICIO",
                false,
                null,
                null,
                List.of("campoFicticio"),
                null,
                referenciaFicticia()))
                .as("campo em branco seria lido como redução de zero")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("redução de zero");
    }

    @Test
    void deveRecusarEvidenciaComValorEncontradoNuloSemMotivo() {
        assertThatThrownBy(() -> new PassoExposto.EvidenciaExposta(
                "campoFicticio", null, null, "00,00", null, "origem fictícia"))
                .as("traço em branco apagaria justamente o motivo do apontamento")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("não trouxe o campo");
    }

    @Test
    void deveRecusarApontamentoSemEvidencia() {
        assertThatThrownBy(() -> new PassoExposto.ExplicacaoExposta(
                "APONTAMENTO", List.of(), referenciaFicticia(), "10.00", null, null))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("sem evidência não é conferível");
    }

    @Test
    void deveRecusarDerivacaoQueChegaComFundamentoNormativo() {
        assertThatThrownBy(() -> new PassoExposto.ExplicacaoExposta(
                "DERIVACAO", List.of(), referenciaFicticia(), null, null, "conta fictícia"))
                .as("o banco não gravou nada para um conforme: não há fundamento a mostrar")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("não tem evidência, fundamento nem valor em risco");
    }

    @Test
    void deveRecusarTipoDeExplicacaoDesconhecido() {
        assertThatThrownBy(() -> new PassoExposto.ExplicacaoExposta(
                "OUTRA_COISA", List.of(), null, null, null, "texto fictício"))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("Tipo de explicação desconhecido");
    }

    @Test
    void deveRecusarPassoComVersaoDeRegraNulaSemMotivo() {
        assertThatThrownBy(() -> new PassoExposto(
                "R99-ficticia",
                null,
                MOTIVO,
                "SEM_DIVERGENCIA_IDENTIFICADA",
                "Sem divergência identificada",
                "explicação fictícia",
                null,
                null,
                new PassoExposto.ExplicacaoExposta(
                        "DERIVACAO", List.of(), null, null, null, "conta fictícia")))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("não grava avaliação conforme");
    }

    @Test
    void deveRecusarCampoDeclaradoEmBrancoSemMotivo() {
        assertThatThrownBy(() -> new DeclaracaoExposta.CampoDeclarado("NCM", null, null))
                .as("célula em branco na tela vira zero na cabeça de quem lê")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("exatamente um dos dois");
    }

    @Test
    void deveRecusarGrupoComNcmNuloSemMotivo() {
        assertThatThrownBy(() -> new GrupoExposto(
                null, null, "FICT-001", null,
                "POSSIVEL_DIVERGENCIA", "Possível divergência", "explicação fictícia",
                "SOMENTE_CLASSTRIB", "rótulo fictício",
                1, 1, "10.00", "valor dos produtos envolvidos",
                quatroEstados(), 0))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("Componente vazio e mudo");
    }

    @Test
    void deveRecusarGrupoComMaisNotasQueProdutos() {
        assertThatThrownBy(() -> new GrupoExposto(
                "00000000", null, "FICT-001", null,
                "POSSIVEL_DIVERGENCIA", "Possível divergência", "explicação fictícia",
                "NCM_E_CLASSTRIB", "rótulo fictício",
                1, 2, "10.00", "valor dos produtos envolvidos",
                quatroEstados(), 0))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("não pode haver mais notas que produtos");
    }

    @Test
    void deveRecusarRespostaDeResultadoSemOAvisoDeUso() {
        assertThatThrownBy(() -> new RespostaDeProdutos(
                "ficticio", PaginaExposta.de(0, 50, 0), List.of(), FAIXA_FICTICIA, null))
                .as("o aviso vem do servidor para nenhuma tela precisar lembrar de escrevê-lo")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("aviso de uso");
    }

    @Test
    void deveRecusarOrdenacaoSemDizerOQueElaSignifica() {
        assertThatThrownBy(() -> new RespostaDeGrupos.OrdemExposta(
                "VALOR_DOS_PRODUTOS", "rótulo fictício", null, true))
                .as("sem o significado, a lista é lida como ranking de gravidade")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("ranking de gravidade");
    }

    @Test
    void deveRecusarBaseTributariaComBlocoDeNcmSemAPerguntaCorrespondente() {
        assertThatThrownBy(() -> new RespostaDaBaseTributaria(
                DATA,
                "carga-ficticia",
                false,
                new LeituraExposta<>(List.of(), MOTIVO),
                List.of(new TratamentoExposto.TributoExposto(
                        "CBS", "CBS", "CBS", new LeituraExposta<>(List.of(), MOTIVO))),
                null,
                new LeituraExposta<>(List.of(), MOTIVO),
                null,
                null,
                null,
                "como consultar fictício",
                FAIXA_FICTICIA,
                "aviso fictício"))
                .as("bloco sem pergunta e pergunta sem bloco são igualmente confusos")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("Bloco sem pergunta");
    }

    @Test
    void deveRecusarBaseTributariaQueDizTerCargaESemCoberturaDeclarada() {
        assertThatThrownBy(() -> new RespostaDaBaseTributaria(
                DATA,
                "carga-ficticia",
                true,
                new LeituraExposta<>(List.of(), MOTIVO),
                List.of(new TratamentoExposto.TributoExposto(
                        "CBS", "CBS", "CBS", new LeituraExposta<>(List.of(), MOTIVO))),
                null,
                null,
                null,
                null,
                null,
                "como consultar fictício",
                FAIXA_FICTICIA,
                "aviso fictício"))
                .as("a cobertura é o que separa registro ausente de tabela não carregada")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("precisam concordar");
    }

    /** Uma faixa de procedência fictícia, para os casos que não são sobre ela. */
    private static final FaixaDeNatureza FAIXA_FICTICIA = FaixaDeNatureza.de(
            br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga.naoDeclarada(),
            "carga-ficticia");

    @Test
    void deveRecusarRespostaDeResultadoSemAFaixaDeProcedencia() {
        assertThatThrownBy(() -> new RespostaDeProdutos(
                "ficticio", PaginaExposta.de(0, 50, 0), List.of(), null, "aviso fictício"))
                .as("dado de demonstração sem aviso é afirmação falsa sobre a lei")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("faixa de procedência");
    }

    @Test
    void aFaixaDeveTrazerRotuloEExplicacaoEnaoSoOCodigo() {
        assertThat(FAIXA_FICTICIA.situacao()).isEqualTo("NAO_DECLARADA");
        assertThat(FAIXA_FICTICIA.rotulo()).isNotBlank();
        assertThat(FAIXA_FICTICIA.explicacao())
                .as("carga anterior à declaração não vira normativa por omissão")
                .contains("Não se supõe que seja normativo");
        assertThat(FAIXA_FICTICIA.exigeAviso()).isTrue();
    }

    private static TratamentoExposto.ReferenciaExposta referenciaFicticia() {
        return new TratamentoExposto.ReferenciaExposta(
                "FONTE FICTICIA PARA TESTE", DATA, DATA, null);
    }

    private static List<EstadoContado> quatroEstados() {
        return List.of(
                new EstadoContado("POSSIVEL_DIVERGENCIA", "r", "e", 1),
                new EstadoContado("REQUER_CONFERENCIA", "r", "e", 0),
                new EstadoContado("NAO_FOI_POSSIVEL_CONCLUIR", "r", "e", 0),
                new EstadoContado("SEM_DIVERGENCIA_IDENTIFICADA", "r", "e", 0));
    }
}
