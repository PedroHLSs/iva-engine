package br.edu.tcc.auditoria.infraestrutura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

// Classe que inicia a aplicação Spring. Fica em infraestrutura para o Spring só varrer esse pacote, deixando aplicacao e dominio sem anotação. Com um comando, roda o comando e sai com o código dele; sem argumento nenhum, sobe a interface web em http://127.0.0.1:8080/ (mudou em 12/09/2026: antes só mostrava a lista de comandos).
@SpringBootApplication
public class AuditoriaApplication {

    // Perfil que liga o servidor web. É ativado aqui, antes de run, porque Argumentos recusaria --spring.profiles.active.
    static final String PERFIL_DA_API = "api";

    // Comando usado quando a chamada não traz nenhum.
    static final String COMANDO_PADRAO = "servir";

    // Sobe o Spring com o comando pedido, ou com servir quando não veio nenhum, e sai com o código do comando.
    public static void main(String[] argumentos) {
        SpringApplication aplicacao = new SpringApplication(AuditoriaApplication.class);

        if (chamadaSemComando(argumentos)) {
            aplicacao.setAdditionalProfiles(PERFIL_DA_API);
        }

        // Fecha o contexto para o código de saída chegar ao terminal; com servir, esta linha só é alcançada quando o servidor para.
        ConfigurableApplicationContext contexto = aplicacao.run(comandoEfetivo(argumentos));
        System.exit(SpringApplication.exit(contexto));
    }

    // Método estático que diz se a chamada veio sem nenhum argumento.
    static boolean chamadaSemComando(String[] argumentos) {
        return argumentos.length == 0;
    }

    // Método estático que devolve os argumentos originais, ou o comando padrão quando não veio nenhum.
    static String[] comandoEfetivo(String[] argumentos) {
        return chamadaSemComando(argumentos) ? new String[] {COMANDO_PADRAO} : argumentos;
    }
}
