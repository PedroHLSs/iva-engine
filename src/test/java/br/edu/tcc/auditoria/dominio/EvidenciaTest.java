package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.EvidenciaInvalida;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenciaTest {

    @Test
    void deveRegistrarValorEncontradoEValorEsperado() {
        Evidencia evidencia = DadosFicticios.evidencia();

        assertThat(evidencia.campoAnalisado()).isEqualTo("campoFicticio");
        assertThat(evidencia.valorEncontrado()).contains("99,99");
        assertThat(evidencia.valorEsperado()).contains("00,00");
    }

    @Test
    void deveRegistrarCampoNaoInformadoComoValorEncontradoVazio() {
        // Campo ausente no documento não vira "0" nem "" na evidência.
        Evidencia evidencia = new Evidencia(
                "campoFicticio",
                Optional.empty(),
                Optional.of("00,00"),
                new OrigemEvidencia.DeTabelaNormativa("TABELA-FICTICIA", "versao-ficticia"));

        assertThat(evidencia.valorEncontrado()).isEmpty();
        assertThat(evidencia.valorEncontrado()).isNotEqualTo(Optional.of("0"));
        assertThat(evidencia.valorEncontrado()).isNotEqualTo(Optional.of(""));
    }

    @Test
    void deveAceitarEvidenciaSemValorEsperado() {
        Evidencia evidencia = new Evidencia(
                "campoFicticio",
                Optional.of("99,99"),
                Optional.empty(),
                new OrigemEvidencia.DaRegra("incoerência interna, sem referência a opor"));

        assertThat(evidencia.valorEsperado()).isEmpty();
    }

    @Test
    void deveDistinguirValorEncontradoAusenteDeValorEncontradoEmBranco() {
        Evidencia ausente = evidenciaComValorEncontrado(Optional.empty());
        Evidencia emBranco = evidenciaComValorEncontrado(Optional.of(""));

        assertThat(ausente).isNotEqualTo(emBranco);
        assertThat(emBranco.valorEncontrado()).isPresent();
    }

    @Test
    void deveRejeitarEvidenciaSemCampoAnalisado() {
        assertThatThrownBy(() -> new Evidencia(
                "  ",
                Optional.empty(),
                Optional.empty(),
                new OrigemEvidencia.DaRegra("qualquer")))
                .isInstanceOf(EvidenciaInvalida.class);
    }

    @Test
    void deveRejeitarEvidenciaSemOrigem() {
        assertThatThrownBy(() -> new Evidencia(
                "campoFicticio",
                Optional.empty(),
                Optional.empty(),
                null))
                .isInstanceOf(EvidenciaInvalida.class)
                .hasMessageContaining("origem");
    }

    @Test
    void deveRejeitarValorEncontradoNuloEmVezDeOptionalVazio() {
        assertThatThrownBy(() -> evidenciaComValorEncontrado(null))
                .isInstanceOf(EvidenciaInvalida.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void deveExigirNomeEVersaoDaTabelaNormativaDeOrigem() {
        assertThatThrownBy(() -> new OrigemEvidencia.DeTabelaNormativa("TABELA-FICTICIA", "  "))
                .isInstanceOf(EvidenciaInvalida.class)
                .hasMessageContaining("versaoTabela");
    }

    @Test
    void deveExigirLocalizacaoQuandoOValorVeioDoDocumento() {
        assertThatThrownBy(() -> new OrigemEvidencia.DoDocumento(null))
                .isInstanceOf(EvidenciaInvalida.class)
                .hasMessageContaining("localizacao");
    }

    private static Evidencia evidenciaComValorEncontrado(Optional<String> valorEncontrado) {
        return new Evidencia(
                "campoFicticio",
                valorEncontrado,
                Optional.empty(),
                new OrigemEvidencia.DoDocumento("posição fictícia no documento"));
    }
}
