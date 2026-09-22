package br.edu.tcc.auditoria.infraestrutura.cli;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Etapa 8 não mexeu no contrato de saída da CLI.
 *
 * <h2>O que este teste guarda</h2>
 *
 * <p>A Etapa 8 acrescentou {@code spring-boot-starter-web} ao classpath, um perfil
 * {@code api} e o comando {@link ComandoServir}. Nenhum arquivo da Etapa 5 mudou,
 * mas as três coisas entram no mesmo processo que roda {@code auditar},
 * {@code exportar} e {@code listar-achados} — e o código de saída é o contrato com
 * quem chama a auditoria de dentro de um script. É a parte que passa despercebida
 * quando quebra.</p>
 *
 * <p>{@code LinhaDeComandoTest} já fixa esses códigos, e continua valendo. Este
 * teste é outro: refaz as mesmas asserções <strong>com {@code ComandoServir}
 * presente no mapa de comandos</strong>, que é a situação nova. Ele é um arquivo
 * separado de propósito — aquele é da Etapa 5, e a regra de etapa proíbe reescrever
 * teste entregue sem autorização.</p>
 *
 * <p>Sem contexto Spring, sem banco e sem Docker.</p>
 */
class CodigosDeSaidaDaCliTest {

    private static final int SUCESSO = 0;
    private static final int ERRO_DE_USO = 2;

    private static final String COMANDO_FALSO = "alfa";

    private final SaidaFalsa saida = new SaidaFalsa();

    @Test
    void chamadaSemComandoContinuaSaindoComErroDeUso() {
        LinhaDeComando linhaDeComando = montarComServir();

        linhaDeComando.run();

        assertThat(linhaDeComando.getExitCode()).isEqualTo(ERRO_DE_USO);
    }

    @Test
    void comandoDesconhecidoContinuaSaindoComErroDeUso() {
        LinhaDeComando linhaDeComando = montarComServir();

        linhaDeComando.run("comando-que-nao-existe");

        assertThat(linhaDeComando.getExitCode()).isEqualTo(ERRO_DE_USO);
    }

    @Test
    void comandoBemSucedidoContinuaSaindoComSucesso() {
        ComandoFalso alfa = new ComandoFalso();
        LinhaDeComando linhaDeComando = montar(alfa, servirSemServidor());

        linhaDeComando.run(COMANDO_FALSO);

        assertThat(alfa.foiExecutado()).isTrue();
        assertThat(linhaDeComando.getExitCode()).isEqualTo(SUCESSO);
    }

    @Test
    void usoInvalidoDeOutroComandoContinuaSaindoComErroDeUso() {
        LinhaDeComando linhaDeComando = montar(
                new ComandoFalso().recusando(new UsoInvalido("Recusa fictícia de uso.")),
                servirSemServidor());

        linhaDeComando.run(COMANDO_FALSO);

        assertThat(saida.texto()).contains("Recusa fictícia de uso.");
        assertThat(linhaDeComando.getExitCode()).isEqualTo(ERRO_DE_USO);
    }

    @Test
    void servirSemOPerfilApiSaiComErroDeUsoEEnsinaAInvocacao() {
        LinhaDeComando linhaDeComando = montarComServir();

        linhaDeComando.run(ComandoServir.NOME);

        assertThat(saida.texto())
                .as("a recusa precisa dizer como chamar direito, e não só que está errado")
                .contains(ComandoServir.INVOCACAO);
        assertThat(linhaDeComando.getExitCode())
                .as("sem servidor o comando recusa em vez de pendurar o processo para sempre")
                .isEqualTo(ERRO_DE_USO);
    }

    @Test
    void aListagemDeComandosGanhouServirEIssoEADiferencaVisivel() {
        LinhaDeComando linhaDeComando = montarComServir();

        linhaDeComando.run();

        assertThat(saida.texto())
                .as("a única mudança observável na CLI sem o perfil api é esta linha na ajuda; "
                        + "está afirmada aqui para que ninguém a descubra por acidente")
                .contains(ComandoServir.NOME)
                // Emenda da Etapa 11: a descrição dizia "somente para leitura",
                // e o comando passou a subir também o envio de documento.
                .contains("análise");
    }

    private LinhaDeComando montarComServir() {
        return montar(servirSemServidor());
    }

    private LinhaDeComando montar(Comando... comandos) {
        return new LinhaDeComando(List.of(comandos), saida);
    }

    /**
     * O {@code servir} de verdade, com um contexto que não é web.
     *
     * <p>{@link GenericApplicationContext} é um contexto real do Spring e
     * <strong>não</strong> implementa {@code WebServerApplicationContext} — é
     * exatamente a situação de quem roda a aplicação sem o perfil {@code api}, em
     * que {@code web-application-type=none} vale.</p>
     */
    private ComandoServir servirSemServidor() {
        return new ComandoServir(new GenericApplicationContext(), saida);
    }

    /** Guarda o que foi escrito, em vez de mandar para o terminal. */
    private static final class SaidaFalsa implements Saida {

        private final List<String> linhas = new ArrayList<>();

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        String texto() {
            return String.join("\n", linhas);
        }
    }

    /** Comando de mentira, com nome fictício para o teste não depender da lista real. */
    private static final class ComandoFalso implements Comando {

        private RuntimeException recusa;
        private boolean executado;

        ComandoFalso recusando(RuntimeException recusa) {
            this.recusa = recusa;
            return this;
        }

        @Override
        public String nome() {
            return COMANDO_FALSO;
        }

        @Override
        public String descricao() {
            return "Descrição fictícia.";
        }

        @Override
        public String modoDeUsar() {
            return "Modo de usar fictício.";
        }

        @Override
        public void executar(Argumentos argumentos) {
            executado = true;
            if (recusa != null) {
                throw recusa;
            }
        }

        boolean foiExecutado() {
            return executado;
        }
    }
}
