package br.edu.tcc.auditoria.infraestrutura.configuracao;

import br.edu.tcc.auditoria.aplicacao.auditoria.OrigemDaTolerancia;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D023 (04/10/2026): a tolerância da R05 é resolvida com a origem. Configurada pela instalação, é CONFIGURADA; sem configuração, vale o padrão declarado no application.properties, e a origem diz PADRAO — o padrão aparece na saída, nunca implícito. Até essa data o padrão ficava escondido no placeholder da propriedade, e o comentário dizia que não havia padrão. Os valores aqui são fictícios.
class ToleranciaComOrigemTest {

    private final ConfiguracaoDaAuditoria configuracao = new ConfiguracaoDaAuditoria();

    @Test
    void toleranciaConfiguradaDeveSairComoConfigurada() {
        MockEnvironment ambiente = new MockEnvironment()
                .withProperty("auditoria.tolerancia-de-valor", "0.07")
                .withProperty("auditoria.tolerancia-de-valor-padrao", "0.99");

        ToleranciaDaExecucao tolerancia = configuracao.toleranciaDaExecucao(ambiente);

        assertThat(tolerancia.quantia()).isEqualTo("0.07");
        assertThat(tolerancia.origem()).isEqualTo(OrigemDaTolerancia.CONFIGURADA);
    }

    @Test
    void semConfiguracaoDeveValerOPadraoDitoComoPadrao() {
        MockEnvironment ambiente = new MockEnvironment()
                .withProperty("auditoria.tolerancia-de-valor", "")
                .withProperty("auditoria.tolerancia-de-valor-padrao", "0.99");

        ToleranciaDaExecucao tolerancia = configuracao.toleranciaDaExecucao(ambiente);

        assertThat(tolerancia.quantia()).isEqualTo("0.99");
        assertThat(tolerancia.origem()).isEqualTo(OrigemDaTolerancia.PADRAO);
        assertThat(tolerancia.texto()).contains("0.99").contains("padrão");
    }

    @Test
    void semConfiguracaoESemPadraoASubidaDeveSerRecusada() {
        assertThatThrownBy(() -> configuracao.toleranciaDaExecucao(new MockEnvironment()))
                .isInstanceOf(ConfiguracaoInvalida.class)
                .hasMessageContaining("auditoria.tolerancia-de-valor-padrao");
    }

    @Test
    void toleranciaQueNaoENumeroDeveSerRecusada() {
        MockEnvironment ambiente = new MockEnvironment().withProperty("auditoria.tolerancia-de-valor", "um centavo");

        assertThatThrownBy(() -> configuracao.toleranciaDaExecucao(ambiente))
                .isInstanceOf(ConfiguracaoInvalida.class);
    }

    // O application.properties real: sem a variável de ambiente, a tolerância é o padrão, e sai como padrão.
    @Test
    void oApplicationPropertiesRealDeveDarOPadraoComoPadrao() throws IOException {
        Properties arquivo = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        MockEnvironment ambiente = new MockEnvironment();
        arquivo.stringPropertyNames().forEach(nome -> ambiente.setProperty(nome, arquivo.getProperty(nome)));

        ToleranciaDaExecucao tolerancia = configuracao.toleranciaDaExecucao(ambiente);

        assertThat(tolerancia.origem()).isEqualTo(OrigemDaTolerancia.PADRAO);
        assertThat(tolerancia.quantia()).isEqualTo(arquivo.getProperty("auditoria.tolerancia-de-valor-padrao"));
    }
}
