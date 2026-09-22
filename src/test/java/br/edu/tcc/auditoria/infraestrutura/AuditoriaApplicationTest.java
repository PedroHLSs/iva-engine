package br.edu.tcc.auditoria.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * A decisão que o ponto de entrada toma antes de chamar {@code run}.
 *
 * <p>O {@code main} não é exercitado aqui: ele termina em {@code System.exit}, e
 * um teste que o chamasse derrubaria a JVM da suíte. O que se pode isolar — e é
 * o que decide se a interface web sobe — são as duas decisões sobre os
 * argumentos recebidos.</p>
 */
class AuditoriaApplicationTest {

    @Test
    void deveAssumirOComandoDeServirQuandoAChamadaNaoTrazNenhum() {
        assertThat(AuditoriaApplication.comandoEfetivo(new String[0]))
                .containsExactly(AuditoriaApplication.COMANDO_PADRAO);
    }

    @Test
    void deveManterOsArgumentosQuandoAChamadaTrazComando() {
        String[] originais = {"auditar", "--origem=dados/lote"};

        assertThat(AuditoriaApplication.comandoEfetivo(originais)).isSameAs(originais);
    }

    @Test
    void deveReconhecerAChamadaSemComando() {
        assertThat(AuditoriaApplication.chamadaSemComando(new String[0])).isTrue();
    }

    @Test
    void naoDeveConfundirChamadaComComandoComChamadaVazia() {
        assertThat(AuditoriaApplication.chamadaSemComando(new String[] {"exportar"})).isFalse();
    }

    /**
     * Sem esta afirmação, trocar o nome do perfil por um que não existe passaria
     * despercebido: o contexto subiria sem camada web e {@code servir} recusaria,
     * que é exatamente o defeito que a mudança veio corrigir.
     */
    @Test
    void oPerfilAtivadoDeveSerOQueTrocaOTipoDeAplicacaoParaServlet() throws Exception {
        String arquivoDoPerfil = "application-" + AuditoriaApplication.PERFIL_DA_API + ".properties";
        Properties propriedades = new Properties();

        try (InputStream conteudo = getClass().getClassLoader().getResourceAsStream(arquivoDoPerfil)) {
            assertThat(conteudo)
                    .as("o perfil \"%s\" precisa ter um %s no classpath",
                            AuditoriaApplication.PERFIL_DA_API, arquivoDoPerfil)
                    .isNotNull();
            propriedades.load(new InputStreamReader(conteudo, StandardCharsets.UTF_8));
        }

        assertThat(propriedades.getProperty("spring.main.web-application-type")).isEqualTo("servlet");
    }
}
