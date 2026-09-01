package br.edu.tcc.auditoria.infraestrutura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Ponto de entrada da aplicação.
 *
 * <h2>Por que a classe do Spring mora em {@code infraestrutura}</h2>
 *
 * <p>Porque é infraestrutura. A varredura de componentes parte do pacote desta
 * classe, e o efeito prático é o desejado: {@code infraestrutura} é o único
 * pacote varrido, {@code aplicacao} e {@code dominio} ficam de fora e continuam
 * sem nenhuma anotação. Os objetos das outras camadas são construídos à mão em
 * {@link br.edu.tcc.auditoria.infraestrutura.configuracao.ConfiguracaoDaAuditoria},
 * o que deixa explícito quem depende de quem.</p>
 *
 * <h2>Não é uma aplicação web</h2>
 *
 * <p>Não há {@code spring-boot-starter-web} no projeto. O contexto sobe, o
 * comando pedido na linha de comando roda, e a aplicação encerra com o código de
 * saída do comando (D006).</p>
 */
@SpringBootApplication
public class AuditoriaApplication {

    public static void main(String[] argumentos) {
        // O contexto é fechado explicitamente para que o código de saída do
        // comando chegue ao terminal: quem roda a auditoria num script precisa
        // saber se ela falhou.
        ConfigurableApplicationContext contexto =
                new SpringApplication(AuditoriaApplication.class).run(argumentos);
        System.exit(SpringApplication.exit(contexto));
    }
}
