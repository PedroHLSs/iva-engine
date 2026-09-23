package br.edu.tcc.auditoria.infraestrutura.cli;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;

// Classe do comando servir, que mantém a interface web e a API no ar até o contexto ser fechado com Ctrl+C. Bloqueia dentro do run() do Spring, então o System.exit do AuditoriaApplication só roda quando o servidor para; sem o perfil api, recusa em vez de ficar parado sem atender nada.
@Component
class ComandoServir implements Comando {

    static final String NOME = "servir";

    // Linha de comando que sobe o servidor, mostrada na ajuda e na recusa.
    static final String INVOCACAO =
            "java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar " + NOME;

    private final ApplicationContext contexto;
    private final Saida saida;
    private final CountDownLatch ateFecharOContexto = new CountDownLatch(1);

    // Construtor que recebe o contexto do Spring e a saída.
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

    // Confere se há servidor web e espera até o contexto ser fechado; sem o perfil api, recusa.
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
        // Mudou na Etapa 11: antes esta linha dizia "Somente GET", o que deixou de valer com o POST /api/analises.
        saida.linha("Leitura e envio de documento para análise. Importar catálogo e tratar achado "
                + "continuam na CLI.");
        esperarOFechamentoDoContexto();
    }

    // Libera a espera quando o contexto é fechado; o aviso chega em outra thread, por isso a espera usa CountDownLatch.
    @EventListener
    void aoFecharOContexto(ContextClosedEvent fechamento) {
        ateFecharOContexto.countDown();
    }

    // Método auxiliar que espera o contexto ser fechado.
    private void esperarOFechamentoDoContexto() {
        try {
            ateFecharOContexto.await();
        } catch (InterruptedException interrompido) {
            // Restaura o sinal de interrupção e devolve, para o processo encerrar como pedido.
            Thread.currentThread().interrupt();
            saida.linha("Encerrando: a espera foi interrompida.");
        }
    }
}
