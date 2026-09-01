package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.DocumentoInvalido;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentoTest {

    @Test
    void deveConstruirDocumentoCompleto() {
        Documento documento = DadosFicticios.documento();

        assertThat(documento.chaveAcesso()).isEqualTo(DadosFicticios.chave());
        assertThat(documento.ufEmitente()).isEqualTo(Uf.SP);
        assertThat(documento.ufDestinatario()).contains(Uf.MG);
    }

    @Test
    void deveConstruirDocumentoSemDestinatarioIdentificado() {
        Documento documento = new Documento(
                DadosFicticios.chave(),
                DadosFicticios.MODELO,
                DadosFicticios.SERIE,
                DadosFicticios.NUMERO,
                DadosFicticios.DATA_EMISSAO,
                Uf.SP,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                DadosFicticios.pseudonimoEmitente(),
                Optional.empty());

        assertThat(documento.identificadorDestinatarioPseudonimizado()).isEmpty();
        assertThat(documento.ufDestinatario()).isEmpty();
    }

    @Test
    void deveApontarOperacaoInterestadualQuandoAsUfsDiferem() {
        assertThat(DadosFicticios.documento().ehInterestadual()).contains(true);
    }

    @Test
    void naoDeveApontarOperacaoInterestadualQuandoAsUfsCoincidem() {
        assertThat(documentoComDestino(Optional.of(Uf.SP)).ehInterestadual()).contains(false);
    }

    @Test
    void naoDeveResponderSobreInterestadualidadeQuandoNaoHaUfDeDestino() {
        // Sem UF de destino não há como afirmar interestadualidade, e o modelo não
        // chuta: responder "false" seria afirmar operação interna sobre um documento
        // que não disse isso.
        assertThat(documentoComDestino(Optional.empty()).ehInterestadual()).isEmpty();
    }

    @Test
    void deveDistinguirOperacaoInternaDeInterestadualidadeDesconhecida() {
        assertThat(documentoComDestino(Optional.of(Uf.SP)).ehInterestadual())
                .as("mesma UF é resposta; ausência de UF de destino é ausência de resposta")
                .isNotEqualTo(documentoComDestino(Optional.empty()).ehInterestadual());
    }

    @Test
    void deveRejeitarChaveDeAcessoNula() {
        assertThatThrownBy(() -> new Documento(
                null,
                DadosFicticios.MODELO,
                DadosFicticios.SERIE,
                DadosFicticios.NUMERO,
                DadosFicticios.DATA_EMISSAO,
                Uf.SP,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                DadosFicticios.pseudonimoEmitente(),
                Optional.empty()))
                .isInstanceOf(DocumentoInvalido.class)
                .hasMessageContaining("chaveAcesso");
    }

    @Test
    void deveRejeitarModeloVazio() {
        assertThatThrownBy(() -> new Documento(
                DadosFicticios.chave(),
                "  ",
                DadosFicticios.SERIE,
                DadosFicticios.NUMERO,
                DadosFicticios.DATA_EMISSAO,
                Uf.SP,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                DadosFicticios.pseudonimoEmitente(),
                Optional.empty()))
                .isInstanceOf(DocumentoInvalido.class)
                .hasMessageContaining("modelo");
    }

    @Test
    void deveRejeitarEmitenteSemPseudonimo() {
        assertThatThrownBy(() -> new Documento(
                DadosFicticios.chave(),
                DadosFicticios.MODELO,
                DadosFicticios.SERIE,
                DadosFicticios.NUMERO,
                DadosFicticios.DATA_EMISSAO,
                Uf.SP,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                null,
                Optional.empty()))
                .isInstanceOf(DocumentoInvalido.class)
                .hasMessageContaining("identificadorEmitentePseudonimizado");
    }

    @Test
    void deveRejeitarOptionalNuloEmVezDeOptionalVazio() {
        assertThatThrownBy(() -> documentoComDestino(null))
                .isInstanceOf(DocumentoInvalido.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void deveRejeitarCodigoPresenteEVazio() {
        // "Presente e em branco" seria uma segunda grafia de ausência. Só existe uma.
        assertThatThrownBy(() -> new Documento(
                DadosFicticios.chave(),
                DadosFicticios.MODELO,
                DadosFicticios.SERIE,
                DadosFicticios.NUMERO,
                DadosFicticios.DATA_EMISSAO,
                Uf.SP,
                Optional.empty(),
                Optional.of("   "),
                Optional.empty(),
                DadosFicticios.pseudonimoEmitente(),
                Optional.empty()))
                .isInstanceOf(DocumentoInvalido.class)
                .hasMessageContaining("crtEmitente");
    }

    private static Documento documentoComDestino(Optional<Uf> ufDestinatario) {
        return new Documento(
                DadosFicticios.chave(),
                DadosFicticios.MODELO,
                DadosFicticios.SERIE,
                DadosFicticios.NUMERO,
                DadosFicticios.DATA_EMISSAO,
                Uf.SP,
                ufDestinatario,
                Optional.empty(),
                Optional.empty(),
                DadosFicticios.pseudonimoEmitente(),
                Optional.empty());
    }
}
