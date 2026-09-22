package br.edu.tcc.auditoria.infraestrutura.cli;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Mantém a API de leitura de pé até o contexto ser fechado.
 *
 * <h2>Por que este comando bloqueia, e por que isso basta</h2>
 *
 * <p>{@code AuditoriaApplication.main} chama {@code run(argumentos)} e, na linha
 * seguinte, {@code System.exit(SpringApplication.exit(contexto))} — sem
 * condicional, e é assim desde a Etapa 5. Se o comando devolvesse, o contexto
 * fecharia e o servidor recém-subido cairia junto.</p>
 *
 * <p>Não há condicional nova em {@code main}. O bloqueio acontece
 * <em>dentro</em> da chamada anterior: {@code SpringApplication.run} inicia o
 * servidor durante {@code refreshContext()} e só depois executa os
 * {@code CommandLineRunner}, na própria thread {@code main}. Enquanto este método
 * não devolve, {@code run()} não retornou, e a linha do {@code System.exit} não
 * foi alcançada. Nenhum arquivo da Etapa 5 precisou mudar (D009).</p>
 *
 * <p>No Ctrl+C, o gancho de encerramento do Spring fecha o contexto, o
 * {@link ContextClosedEvent} libera a espera, este método devolve, {@code run()}
 * retorna e o {@code System.exit} original roda normalmente.</p>
 *
 * <h2>Sem servidor, recusa em vez de pendurar</h2>
 *
 * <p>{@code application.properties} mantém {@code web-application-type=none}, e só
 * o perfil {@code api} o troca por {@code servlet}. Sem o perfil não há servidor
 * nenhum, e bloquear ali deixaria o processo parado para sempre sem atender nada —
 * o pior desfecho possível. Então o comando confere se o contexto é web e, se não
 * for, recusa dizendo a invocação exata.</p>
 */
@Component
class ComandoServir implements Comando {

    static final String NOME = "servir";

    static final String INVOCACAO =
            "java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar " + NOME;

    private final ApplicationContext contexto;
    private final Saida saida;
    private final CountDownLatch ateFecharOContexto = new CountDownLatch(1);

    ComandoServir(ApplicationContext contexto, Saida saida) {
        this.contexto = contexto;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Sobe a interface web e a API: envio de documento para análise e leitura do que já foi gravado.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s

                  Não recebe opção nenhuma. O endereço e a porta são configuração:
                  a API escuta só em 127.0.0.1, e a porta vem de
                  AUDITORIA_API_PORTA (padrão 8080).

                  O perfil "api" NÃO pode entrar como argumento — este comando não
                  aceita opções, e o interpretador de argumentos exige que o
                  primeiro item seja o nome do comando. Ele entra por propriedade
                  de sistema ou por variável de ambiente:

                    %s
                    SPRING_PROFILES_ACTIVE=api java -jar auditoria-ibs-cbs-<versao>.jar %s

                  Encerre com Ctrl+C.
                """.formatted(NOME, INVOCACAO, NOME);
    }

    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of());

        if (!(contexto instanceof WebServerApplicationContext)) {
            throw new UsoInvalido(
                    ("Não há servidor a manter de pé: o perfil \"api\" não está ativo, e sem ele o "
                            + "sistema sobe sem camada web. Chame assim:%n%n    %s")
                            .formatted(INVOCACAO));
        }

        saida.linha("API no ar. Encerre com Ctrl+C.");
        // Emenda da Etapa 11: até a Etapa 10 esta linha dizia "Somente GET", e a
        // frase deixou de ser verdadeira quando POST /api/analises passou a
        // existir. O que continua só na CLI é o que a D009 recusou por motivo
        // que não mudou.
        saida.linha("Leitura e envio de documento para análise. Importar catálogo e tratar achado "
                + "continuam na CLI.");
        esperarOFechamentoDoContexto();
    }

    /**
     * Devolve o {@link ContextClosedEvent} para quem está esperando.
     *
     * <p>Chega na thread do gancho de encerramento, não na {@code main} — daí a
     * espera ser numa {@link CountDownLatch}, e não numa variável qualquer.</p>
     */
    @EventListener
    void aoFecharOContexto(ContextClosedEvent fechamento) {
        ateFecharOContexto.countDown();
    }

    private void esperarOFechamentoDoContexto() {
        try {
            ateFecharOContexto.await();
        } catch (InterruptedException interrompido) {
            // Restaura o sinal e devolve: quem interrompeu quer que o processo
            // encerre, e engolir o sinal faria a thread main seguir como se nada
            // tivesse acontecido.
            Thread.currentThread().interrupt();
            saida.linha("Encerrando: a espera foi interrompida.");
        }
    }
}
