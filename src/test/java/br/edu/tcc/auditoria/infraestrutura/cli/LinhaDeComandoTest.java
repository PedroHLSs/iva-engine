package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaInvalida;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Despacho de comandos, tratamento de erro e código de saída.
 *
 * <p>{@code ArgumentosTest} já cobre a interpretação dos argumentos. O que falta
 * é o que {@link LinhaDeComando} faz com eles: achar o comando, decidir o que
 * imprimir quando não acha, e devolver o código de saída certo.</p>
 *
 * <p>O código de saída é conferido em todos os casos, e não só o texto: é o
 * contrato com quem roda a auditoria dentro de um script, e é a parte que passa
 * despercebida quando quebra.</p>
 *
 * <p>Sem contexto Spring, sem banco e sem Docker — {@code run} é chamado direto.
 * Na aplicação de verdade quem o chama é o Spring, depois de o contexto inteiro
 * subir; aqui interessa só o que o objeto faz, e não quando ele é chamado.</p>
 */
class LinhaDeComandoTest {

    private static final String ALFA = "alfa";
    private static final String ZETA = "zeta";

    private final SaidaFalsa saida = new SaidaFalsa();

    @Test
    void deveListarOsComandosQuandoChamadoSemArgumentoNenhum() {
        ComandoFalso alfa = new ComandoFalso(ALFA);
        LinhaDeComando linhaDeComando = montar(alfa);

        linhaDeComando.run();

        assertThat(saida.texto())
                .contains("Auditoria de coerência de IBS/CBS")
                .contains("Comandos:")
                .contains(ALFA)
                .contains(alfa.descricao());
        assertThat(linhaDeComando.getExitCode())
                .as("chamada sem comando é erro de uso, e quem roda em script precisa saber")
                .isEqualTo(2);
    }

    @Test
    void deveListarOsComandosEmOrdemAlfabetica() {
        LinhaDeComando linhaDeComando = montar(new ComandoFalso(ZETA), new ComandoFalso(ALFA));

        linhaDeComando.run();

        assertThat(saida.texto())
                .as("a ordem sai do nome do comando, e não da ordem em que o Spring os injetou")
                .containsSubsequence(ALFA, ZETA);
    }

    @Test
    void deveRecusarComandoDesconhecidoMostrandoALista() {
        ComandoFalso alfa = new ComandoFalso(ALFA);
        LinhaDeComando linhaDeComando = montar(alfa);

        linhaDeComando.run("comando-que-nao-existe");

        assertThat(saida.linhas().get(0)).isEqualTo("Comando desconhecido: \"comando-que-nao-existe\".");
        assertThat(saida.texto())
                .as("quem errou o nome do comando precisa ver quais existem")
                .contains("Comandos:")
                .contains(ALFA);
        assertThat(alfa.foiExecutado()).isFalse();
        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
    }

    @Test
    void deveExecutarOComandoPedidoComOsArgumentosJaInterpretados() {
        ComandoFalso alfa = new ComandoFalso(ALFA);
        LinhaDeComando linhaDeComando = montar(alfa, new ComandoFalso(ZETA));

        linhaDeComando.run(ALFA, "--origem=pasta-ficticia", "--sinalizador");

        assertThat(alfa.foiExecutado()).isTrue();
        assertThat(alfa.argumentosRecebidos().comando()).isEqualTo(ALFA);
        assertThat(alfa.argumentosRecebidos().texto("origem")).contains("pasta-ficticia");
        assertThat(alfa.argumentosRecebidos().sinalizador("sinalizador")).isTrue();
        assertThat(linhaDeComando.getExitCode())
                .as("comando que rodou até o fim sai com zero")
                .isZero();
    }

    @Test
    void deveMostrarOModoDeUsarDoComandoQuandoOUsoEInvalido() {
        ComandoFalso alfa = new ComandoFalso(ALFA)
                .lancando(new UsoInvalido("A opção \"--origem\" é obrigatória."));
        LinhaDeComando linhaDeComando = montar(alfa, new ComandoFalso(ZETA));

        linhaDeComando.run(ALFA);

        assertThat(saida.texto())
                .contains("A opção \"--origem\" é obrigatória.")
                .contains(alfa.modoDeUsar());
        assertThat(saida.texto())
                .as("o modo de usar é o do comando chamado, não o dos outros")
                .doesNotContain(ZETA);
        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
    }

    @Test
    void naoDeveMostrarModoDeUsarQuandoOErroDeUsoNaoIdentificaComando() {
        ComandoFalso alfa = new ComandoFalso(ALFA);
        LinhaDeComando linhaDeComando = montar(alfa);

        // O primeiro argumento é uma opção, então nem chega a existir nome de
        // comando: quem recusa é o próprio Argumentos, antes do despacho.
        linhaDeComando.run("--origem=pasta-ficticia");

        assertThat(saida.linhas())
                .as("sem comando identificado não há modo de usar a mostrar, e a mensagem basta")
                .hasSize(1);
        assertThat(saida.texto()).doesNotContain(alfa.modoDeUsar());
        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
    }

    @Test
    void deveMostrarSoAMensagemQuandoARecusaJaSeExplica() {
        ComandoFalso alfa = new ComandoFalso(ALFA)
                .lancando(new ConsultaInvalida("Não há apontamento com o identificador informado."));
        LinhaDeComando linhaDeComando = montar(alfa);

        linhaDeComando.run(ALFA);

        assertThat(saida.linhas())
                .as("a mensagem já explica; modo de usar e pilha só afogariam a explicação")
                .containsExactly("Não há apontamento com o identificador informado.");
        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
    }

    @Test
    void deveMostrarSoAMensagemQuandoORecusadoEODominio() {
        ComandoFalso alfa = new ComandoFalso(ALFA)
                .lancando(new CatalogoInvalido("O catálogo recusou a carga."));
        LinhaDeComando linhaDeComando = montar(alfa);

        linhaDeComando.run(ALFA);

        assertThat(saida.linhas()).containsExactly("O catálogo recusou a carga.");
        assertThat(linhaDeComando.getExitCode()).isEqualTo(2);
    }

    @Test
    void deveDeixarFalhaInesperadaSubirComAPilha() {
        ComandoFalso alfa = new ComandoFalso(ALFA)
                .lancando(new IllegalStateException("falha inesperada ficticia"));
        LinhaDeComando linhaDeComando = montar(alfa);

        assertThatThrownBy(() -> linhaDeComando.run(ALFA))
                .as("erro de programação não é erro de uso: aí a pilha é a informação útil")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha inesperada ficticia");

        assertThat(saida.linhas())
                .as("nada é impresso no lugar da pilha")
                .isEmpty();
    }

    private LinhaDeComando montar(Comando... comandos) {
        return new LinhaDeComando(List.of(comandos), saida);
    }

    /**
     * Guarda o que foi escrito, em vez de mandar para o terminal.
     *
     * <p>Só o método abstrato é implementado: os dois {@code default} de
     * {@link Saida} desembocam nele.</p>
     */
    private static final class SaidaFalsa implements Saida {

        private final List<String> linhas = new ArrayList<>();

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        List<String> linhas() {
            return List.copyOf(linhas);
        }

        /** Tudo o que foi escrito, para as verificações por trecho. */
        String texto() {
            return String.join("\n", linhas);
        }
    }

    /**
     * Comando de mentira: registra se foi executado e com quais argumentos, e
     * pode ser configurado para falhar de um jeito escolhido.
     *
     * <p>Os nomes são fictícios de propósito. O teste é do despacho, e não pode
     * quebrar quando a lista de comandos do sistema mudar.</p>
     */
    private static final class ComandoFalso implements Comando {

        private final String nome;
        private Optional<RuntimeException> falha = Optional.empty();
        private Argumentos argumentosRecebidos;

        ComandoFalso(String nome) {
            this.nome = nome;
        }

        /** Faz este comando lançar a exceção indicada quando executado. */
        ComandoFalso lancando(RuntimeException falha) {
            this.falha = Optional.of(falha);
            return this;
        }

        @Override
        public String nome() {
            return nome;
        }

        @Override
        public String descricao() {
            return "Descrição fictícia do comando " + nome + ".";
        }

        @Override
        public String modoDeUsar() {
            return "Modo de usar fictício do comando " + nome + ".";
        }

        @Override
        public void executar(Argumentos argumentos) {
            this.argumentosRecebidos = argumentos;
            falha.ifPresent(erro -> {
                throw erro;
            });
        }

        boolean foiExecutado() {
            return argumentosRecebidos != null;
        }

        Argumentos argumentosRecebidos() {
            return argumentosRecebidos;
        }
    }
}
