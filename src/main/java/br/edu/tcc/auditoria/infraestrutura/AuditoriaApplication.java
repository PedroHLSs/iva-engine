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
 * <p><strong>Cláusula de emenda — 12/09/2026.</strong> Este título e o parágrafo
 * que o seguia deixaram de valer. O texto original, escrito na Etapa 5, dizia:</p>
 *
 * <blockquote>Não há {@code spring-boot-starter-web} no projeto. O contexto sobe,
 * o comando pedido na linha de comando roda, e a aplicação encerra com o código
 * de saída do comando (D006).</blockquote>
 *
 * <p>A primeira frase é falsa desde a <strong>Etapa 8</strong>, que acrescentou
 * {@code spring-boot-starter-web} ao {@code pom.xml} e, com ele, o Tomcat
 * embutido (D009). O parágrafo sobreviveu àquela etapa porque ela não alterou
 * nenhum arquivo anterior — o perfil {@code api} sobrepõe
 * {@code spring.main.web-application-type} sem editar a base —, e por isso
 * continuou afirmando o contrário do que o {@code pom.xml} mostra.</p>
 *
 * <p>O resto do parágrafo continua verdadeiro, e é o caminho normal: toda
 * chamada que <em>traz um comando</em> sobe o contexto, executa aquele comando e
 * encerra com o código de saída dele. Nada disso mudou.</p>
 *
 * <h2>Chamada sem nenhum argumento sobe a interface web</h2>
 *
 * <p>Quando {@code argumentos} vem vazio — que é o que o botão de execução de um
 * IDE faz —, esta classe assume o comando {@link #COMANDO_PADRAO} e ativa o
 * perfil {@link #PERFIL_DA_API}. O efeito é a interface web de pé em
 * {@code http://127.0.0.1:8080/}, sem configuração de execução nenhuma.</p>
 *
 * <p>O perfil é ativado <em>aqui</em>, e não por
 * {@code spring.profiles.active} na base, porque a propriedade da base valeria
 * para todas as chamadas: {@code auditar}, {@code exportar} e
 * {@code importar-catalogo} passariam a subir o Tomcat e a disputar a porta 8080
 * em qualquer script. A D009 afirma que sem o perfil nada muda, e esta forma
 * preserva a afirmação — quem passa um comando não é afetado por nada do que
 * está escrito aqui.</p>
 *
 * <p><strong>O que mudou de observável:</strong> a chamada sem argumento
 * imprimia a listagem geral de comandos e saía com código 2. Agora ela serve. A
 * listagem continua alcançável por qualquer palavra que não seja comando — por
 * exemplo {@code ajuda} —, porque {@code LinhaDeComando} escreve o modo de usar
 * geral ao recusar comando desconhecido. E {@code LinhaDeComando} não foi
 * tocada: o ramo de argumento vazio continua lá, com o mesmo código de saída,
 * para quem a chama diretamente.</p>
 */
@SpringBootApplication
public class AuditoriaApplication {

    /**
     * Perfil que troca {@code spring.main.web-application-type} de {@code none}
     * para {@code servlet}.
     *
     * <p>Ele não pode entrar como argumento de programa — {@code Argumentos.de}
     * exige {@code <comando> [--opcao=valor]} e recusaria
     * {@code --spring.profiles.active}. Daí ser ativado pelo objeto da
     * aplicação, antes de {@code run}, e não acrescentado a
     * {@code argumentos}.</p>
     */
    static final String PERFIL_DA_API = "api";

    /** Comando assumido quando a chamada não traz nenhum. */
    static final String COMANDO_PADRAO = "servir";

    public static void main(String[] argumentos) {
        SpringApplication aplicacao = new SpringApplication(AuditoriaApplication.class);

        if (chamadaSemComando(argumentos)) {
            aplicacao.setAdditionalProfiles(PERFIL_DA_API);
        }

        // O contexto é fechado explicitamente para que o código de saída do
        // comando chegue ao terminal: quem roda a auditoria num script precisa
        // saber se ela falhou.
        //
        // Com o comando "servir" esta linha não é alcançada enquanto o servidor
        // está de pé: ComandoServir bloqueia dentro de run().
        ConfigurableApplicationContext contexto = aplicacao.run(comandoEfetivo(argumentos));
        System.exit(SpringApplication.exit(contexto));
    }

    /** Verdadeiro quando ninguém pediu comando nenhum. */
    static boolean chamadaSemComando(String[] argumentos) {
        return argumentos.length == 0;
    }

    /**
     * Os argumentos que chegam ao {@code CommandLineRunner}: os originais, ou o
     * comando padrão quando não veio nenhum.
     */
    static String[] comandoEfetivo(String[] argumentos) {
        return chamadaSemComando(argumentos) ? new String[] {COMANDO_PADRAO} : argumentos;
    }
}
