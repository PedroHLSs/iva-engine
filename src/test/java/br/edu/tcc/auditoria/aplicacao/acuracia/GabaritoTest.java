package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;

import org.junit.jupiter.api.Test;

import java.util.List;

import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.CHAVE;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.OUTRA_CHAVE;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.REGRA_PRIMEIRA;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.REGRA_SEGUNDA;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.endereco;
import static br.edu.tcc.auditoria.aplicacao.acuracia.CenarioDeAcuracia.linha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GabaritoTest {

    @Test
    void deveResponderORotuloDeCadaEndereco() {
        Gabarito gabarito = new Gabarito(List.of(
                linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO),
                linha(3, CHAVE, 2, REGRA_PRIMEIRA, RotuloEsperado.CONFORME)));

        assertThat(gabarito.rotuloDe(endereco(CHAVE, 1, REGRA_PRIMEIRA)))
                .contains(RotuloEsperado.ACHADO);
        assertThat(gabarito.rotuloDe(endereco(CHAVE, 2, REGRA_PRIMEIRA)))
                .contains(RotuloEsperado.CONFORME);
    }

    @Test
    void deveResponderVazioParaEnderecoQueNaoRotula() {
        Gabarito gabarito = new Gabarito(List.of(
                linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO)));

        assertThat(gabarito.rotuloDe(endereco(OUTRA_CHAVE, 1, REGRA_PRIMEIRA))).isEmpty();
        assertThat(gabarito.contem(endereco(CHAVE, 1, REGRA_SEGUNDA))).isFalse();
    }

    @Test
    void mesmoItemEmRegrasDiferentesSaoEnderecosDiferentes() {
        Gabarito gabarito = new Gabarito(List.of(
                linha(2, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO),
                linha(3, CHAVE, 1, REGRA_SEGUNDA, RotuloEsperado.CONFORME)));

        assertThat(gabarito.quantidadeDeLinhas()).isEqualTo(2);
        assertThat(gabarito.rotuloDe(endereco(CHAVE, 1, REGRA_SEGUNDA)))
                .contains(RotuloEsperado.CONFORME);
    }

    @Test
    void deveRecusarOMesmoEnderecoRotuladoDuasVezesComRotulosDiferentes() {
        List<LinhaDeGabarito> linhas = List.of(
                linha(4, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO),
                linha(9, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.CONFORME));

        assertThatThrownBy(() -> new Gabarito(linhas))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("linhas 4 e 9")
                .hasMessageContaining("não há como saber qual é a verdade de referência");
    }

    @Test
    void deveRecusarOMesmoEnderecoRepetidoAindaQueOsRotulosConcordem() {
        List<LinhaDeGabarito> linhas = List.of(
                linha(4, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO),
                linha(9, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO));

        assertThatThrownBy(() -> new Gabarito(linhas))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("contaria duas vezes");
    }

    @Test
    void deveListarAsRegrasCitadasNaOrdemEmQueAparecem() {
        Gabarito gabarito = new Gabarito(List.of(
                linha(2, CHAVE, 1, REGRA_SEGUNDA, RotuloEsperado.ACHADO),
                linha(3, CHAVE, 1, REGRA_PRIMEIRA, RotuloEsperado.ACHADO),
                linha(4, CHAVE, 2, REGRA_SEGUNDA, RotuloEsperado.ACHADO)));

        assertThat(gabarito.regrasCitadas()).containsExactly(REGRA_SEGUNDA, REGRA_PRIMEIRA);
    }

    @Test
    void gabaritoSemLinhaDeveSeDeclararVazio() {
        assertThat(new Gabarito(List.of()).vazio()).isTrue();
    }

    @Test
    void deveRecusarListaDeLinhasNula() {
        assertThatThrownBy(() -> new Gabarito(null))
                .isInstanceOf(AvaliacaoDeAcuraciaInvalida.class)
                .hasMessageContaining("nunca nula");
    }
}
