package br.edu.tcc.auditoria.infraestrutura.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArgumentosTest {

    @Test
    void deveLerOComandoEAsOpcoes() {
        Argumentos argumentos = Argumentos.de("auditar", "--origem=dados/agosto", "--limite=10");

        assertThat(argumentos.comando()).isEqualTo("auditar");
        assertThat(argumentos.texto("origem")).contains("dados/agosto");
        assertThat(argumentos.inteiro("limite", 50)).isEqualTo(10);
    }

    @Test
    void deveTratarOpcaoSemValorComoSinalizador() {
        Argumentos argumentos = Argumentos.de("listar-achados", "--apenas-abertos");

        assertThat(argumentos.sinalizador("apenas-abertos")).isTrue();
        assertThat(argumentos.sinalizador("limite")).isFalse();
    }

    @Test
    void deveAceitarValorComSinalDeIgual() {
        Argumentos argumentos = Argumentos.de(
                "tratar-achado", "--justificativa=item corrigido em carta = de correcao");

        assertThat(argumentos.texto("justificativa"))
                .as("só o primeiro \"=\" separa nome de valor; o resto é conteúdo")
                .contains("item corrigido em carta = de correcao");
    }

    @Test
    void deveDevolverVazioParaOpcaoNaoInformada() {
        Argumentos argumentos = Argumentos.de("auditar");

        assertThat(argumentos.texto("origem")).isEmpty();
        assertThat(argumentos.inteiro("limite", 50)).isEqualTo(50);
    }

    @Test
    void deveExigirOComandoAntesDasOpcoes() {
        assertThatThrownBy(() -> Argumentos.de("--origem=dados"))
                .isInstanceOf(UsoInvalido.class);
        assertThatThrownBy(() -> Argumentos.de())
                .isInstanceOf(UsoInvalido.class);
    }

    @Test
    void deveRecusarArgumentoSolto() {
        assertThatThrownBy(() -> Argumentos.de("auditar", "dados/agosto"))
                .as("caminho sem \"--origem=\" seria adivinhação sobre o que o usuário quis")
                .isInstanceOf(UsoInvalido.class);
    }

    @Test
    void deveRecusarOpcaoRepetida() {
        assertThatThrownBy(() -> Argumentos.de("auditar", "--origem=um", "--origem=outro"))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void deveRecusarOpcaoDesconhecida() {
        Argumentos argumentos = Argumentos.de("auditar", "--orgem=dados");

        assertThatThrownBy(() -> argumentos.exigirSomente(List.of("origem")))
                .as("opção com erro de digitação não pode ser ignorada em silêncio")
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("orgem");
    }

    @Test
    void deveExigirOpcaoObrigatoria() {
        Argumentos argumentos = Argumentos.de("auditar");

        assertThatThrownBy(() -> argumentos.textoObrigatorio("origem"))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("--origem");
    }

    @Test
    void deveConverterCaminhoEIdentificador() {
        UUID identificador = UUID.randomUUID();
        Argumentos argumentos = Argumentos.de(
                "tratar-achado", "--origem=dados", "--achado=" + identificador);

        assertThat(argumentos.caminhoObrigatorio("origem")).isEqualTo(Path.of("dados"));
        assertThat(argumentos.identificadorObrigatorio("achado")).isEqualTo(identificador);
    }

    @Test
    void deveRecusarIdentificadorMalformado() {
        Argumentos argumentos = Argumentos.de("tratar-achado", "--achado=nao-e-um-identificador");

        assertThatThrownBy(() -> argumentos.identificadorObrigatorio("achado"))
                .isInstanceOf(UsoInvalido.class);
    }

    @Test
    void deveRecusarNumeroMalformado() {
        Argumentos argumentos = Argumentos.de("listar-achados", "--limite=muitos");

        assertThatThrownBy(() -> argumentos.inteiro("limite", 50))
                .isInstanceOf(UsoInvalido.class);
    }
}
