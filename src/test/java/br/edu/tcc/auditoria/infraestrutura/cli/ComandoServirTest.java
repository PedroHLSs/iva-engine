package br.edu.tcc.auditoria.infraestrutura.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O mecanismo que mantém a API de pé sem alterar {@code AuditoriaApplication}.
 *
 * <h2>O que está sendo provado</h2>
 *
 * <p>{@code AuditoriaApplication.main} chama {@code run(argumentos)} e, na linha
 * seguinte, {@code System.exit} — sem condicional, e a Etapa 8 não mudou isso.
 * O servidor só continua de pé porque este comando não devolve enquanto o
 * contexto não fecha, e o {@code CommandLineRunner} roda dentro de
 * {@code run()}.</p>
 *
 * <p>As duas direções importam, e as duas estão aqui: <strong>com</strong>
 * servidor no contexto ele bloqueia e só solta no fechamento;
 * <strong>sem</strong> servidor ele recusa em vez de pendurar o processo para
 * sempre atendendo nada — que seria o pior desfecho possível, porque não dá erro
 * nem funciona.</p>
 */
class ComandoServirTest {

    private static final long ESPERA_MAXIMA_EM_MILISSEGUNDOS = 5_000;

    private final SaidaFalsa saida = new SaidaFalsa();

    @Test
    void deveRecusarQuandoOContextoNaoTemServidor() {
        ComandoServir comando = new ComandoServir(new GenericApplicationContext(), saida);

        assertThatThrownBy(() -> comando.executar(Argumentos.de(ComandoServir.NOME)))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("o perfil \"api\" não está ativo")
                .as("a mensagem tem de trazer a invocação exata, com o -D antes do -jar")
                .hasMessageContaining(ComandoServir.INVOCACAO);
    }

    @Test
    void deveRecusarQualquerOpcao() {
        ComandoServir comando = new ComandoServir(new ContextoComServidorFalso(), saida);

        assertThatThrownBy(() ->
                comando.executar(Argumentos.de(ComandoServir.NOME, "--porta=9999")))
                .as("endereço e porta são configuração da instalação, não opção de linha de comando")
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("porta");
    }

    @Test
    void deveBloquearEnquantoOContextoEstiverAberto() throws InterruptedException {
        ContextoComServidorFalso contexto = new ContextoComServidorFalso();
        ComandoServir comando = new ComandoServir(contexto, saida);

        Thread execucao = executarEmOutraThread(comando);
        esperarAteAnunciarQueEstaNoAr();

        assertThat(execucao.isAlive())
                .as("o comando não pode devolver com o contexto aberto: devolver faria a thread main "
                        + "chegar no System.exit e derrubar o servidor recém-subido")
                .isTrue();

        execucao.interrupt();
        execucao.join(ESPERA_MAXIMA_EM_MILISSEGUNDOS);
    }

    @Test
    void deveDevolverQuandoOContextoFechar() throws InterruptedException {
        ContextoComServidorFalso contexto = new ContextoComServidorFalso();
        ComandoServir comando = new ComandoServir(contexto, saida);

        Thread execucao = executarEmOutraThread(comando);
        esperarAteAnunciarQueEstaNoAr();

        // É o que o gancho de encerramento do Spring faz no Ctrl+C, e chega numa
        // thread que não é a que está esperando — daí a espera ser numa latch.
        comando.aoFecharOContexto(new ContextClosedEvent(contexto));
        execucao.join(ESPERA_MAXIMA_EM_MILISSEGUNDOS);

        assertThat(execucao.isAlive())
                .as("fechado o contexto, o comando devolve e o System.exit original roda")
                .isFalse();
    }

    /**
     * Emenda da Etapa 11.
     *
     * <p>Este teste afirmava "Somente GET", e a frase deixou de ser verdadeira
     * quando {@code POST /api/analises} passou a existir. A propriedade que ele
     * guarda não mudou e continua valendo: <strong>quem sobe o servidor precisa
     * saber, ali mesmo, o que continua sendo só na CLI</strong>. O que mudou foi
     * a lista — importar catálogo e tratar achado, e não mais "tudo".</p>
     */
    @Test
    void deveAnunciarOQueContinuaSendoSoNaCli() throws InterruptedException {
        ComandoServir comando = new ComandoServir(new ContextoComServidorFalso(), saida);

        Thread execucao = executarEmOutraThread(comando);
        esperarAteAnunciarQueEstaNoAr();

        assertThat(saida.texto())
                .as("quem sobe o servidor precisa saber, ali mesmo, o que não passou para a web")
                .contains("Importar catálogo e tratar achado continuam na CLI");
        assertThat(saida.texto())
                .as("e a frase antiga não pode sobreviver: ela afirmaria que nada grava")
                .doesNotContain("Somente GET");

        execucao.interrupt();
        execucao.join(ESPERA_MAXIMA_EM_MILISSEGUNDOS);
    }

    private Thread executarEmOutraThread(ComandoServir comando) {
        Thread execucao = new Thread(
                () -> comando.executar(Argumentos.de(ComandoServir.NOME)), "servir-de-teste");
        execucao.setDaemon(true);
        execucao.start();
        return execucao;
    }

    /**
     * Espera o anúncio que o comando imprime imediatamente antes de bloquear.
     *
     * <p>Visto o anúncio, o comando ou já está esperando na latch ou está a um
     * passo dela, e em nenhum dos dois casos pode terminar por conta própria —
     * então as verificações que vêm depois não têm corrida.</p>
     */
    private void esperarAteAnunciarQueEstaNoAr() throws InterruptedException {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_EM_MILISSEGUNDOS;
        while (!saida.texto().contains("no ar") && System.currentTimeMillis() < limite) {
            Thread.sleep(10);
        }
        assertThat(saida.texto())
                .as("o comando deveria ter anunciado que subiu antes de bloquear")
                .contains("no ar");
    }

    /** Guarda o que foi escrito. Lista concorrente: duas threads a tocam. */
    private static final class SaidaFalsa implements Saida {

        private final List<String> linhas = new CopyOnWriteArrayList<>();

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        String texto() {
            return String.join("\n", linhas);
        }
    }

    /**
     * Contexto que satisfaz {@code instanceof WebServerApplicationContext}.
     *
     * <p>{@code getWebServer()} devolve nulo porque o comando nunca o chama: ele
     * só pergunta se há camada web, e é essa a pergunta que precisa ser respondida
     * em teste. Subir um Tomcat de verdade aqui mediria o Spring, não o
     * comando — o teste de contrato da API faz isso, com servidor real.</p>
     */
    private static final class ContextoComServidorFalso extends GenericApplicationContext
            implements WebServerApplicationContext {

        @Override
        public WebServer getWebServer() {
            return null;
        }

        @Override
        public String getServerNamespace() {
            return null;
        }
    }
}
