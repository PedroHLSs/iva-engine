package br.edu.tcc.auditoria.infraestrutura.sal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cascata de resolução do sal, nos três caminhos.
 *
 * <h2>Nenhum teste aqui toca a máquina de quem roda</h2>
 *
 * <p>Todos usam {@code ArquivoDeSalLocal.em(...)} sobre um diretório temporário.
 * Um teste que escrevesse em {@code %APPDATA%} ou em {@code ~/.config} deixaria
 * um sal para trás e, pior, poderia sobrescrever o da instalação de quem rodou a
 * suíte.</p>
 *
 * <h2>Os valores são fictícios</h2>
 *
 * <p>Os sais escritos à mão aqui são sequências óbvias de {@code a} e {@code b}
 * com o comprimento mínimo. Nada aqui é segredo de nada.</p>
 */
class ResolvedorDeSalTest {

    private static final String SAL_DE_PROPRIEDADE = "sal-ficticio-de-propriedade-aaaaaaaaaaaa";
    private static final String SAL_DE_AMBIENTE = "sal-ficticio-de-ambiente-bbbbbbbbbbbbbbb";
    private static final String SAL_DE_ARQUIVO = "sal-ficticio-de-arquivo-cccccccccccccccc";

    @Test
    void deveResolverDaPropriedadeDeConfiguracaoQuandoEstiverDeclarada(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("sal");
        ResolvedorDeSal resolvedor = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo));

        SalResolvido resolvido = resolvedor.resolver(SAL_DE_PROPRIEDADE, SAL_DE_AMBIENTE);

        assertThat(resolvido.origem()).isEqualTo(OrigemDoSal.PROPRIEDADE_DE_CONFIGURACAO);
        assertThat(resolvido.sal().valor()).isEqualTo(SAL_DE_PROPRIEDADE);
        assertThat(resolvido.arquivo()).isEmpty();
        assertThat(arquivo)
                .as("configuração explícita não escreve arquivo nenhum")
                .doesNotExist();
    }

    @Test
    void deveResolverDaVariavelDeAmbienteQuandoNaoHouverPropriedade(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("sal");
        ResolvedorDeSal resolvedor = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo));

        SalResolvido resolvido = resolvedor.resolver(null, SAL_DE_AMBIENTE);

        assertThat(resolvido.origem()).isEqualTo(OrigemDoSal.VARIAVEL_DE_AMBIENTE);
        assertThat(resolvido.sal().valor()).isEqualTo(SAL_DE_AMBIENTE);
        assertThat(arquivo).doesNotExist();
    }

    /**
     * O Spring faz ligação relaxada, e a variável de ambiente chega também como
     * valor da propriedade. Quando os dois textos são iguais, quem explica a
     * instalação é a variável — e é isso que o diagnóstico precisa dizer para não
     * mandar a pessoa procurar num arquivo de propriedades onde não há nada.
     */
    @Test
    void deveAtribuirAVariavelDeAmbienteQuandoOsDoisValoresForemOMesmoTexto(@TempDir Path pasta) {
        ResolvedorDeSal resolvedor = new ResolvedorDeSal(ArquivoDeSalLocal.em(pasta.resolve("sal")));

        SalResolvido resolvido = resolvedor.resolver(SAL_DE_AMBIENTE, SAL_DE_AMBIENTE);

        assertThat(resolvido.origem()).isEqualTo(OrigemDoSal.VARIAVEL_DE_AMBIENTE);
    }

    @Test
    void deveResolverDoArquivoLocalQuandoNaoHouverNadaConfigurado(@TempDir Path pasta)
            throws Exception {

        Path arquivo = pasta.resolve("sal");
        Files.writeString(arquivo, SAL_DE_ARQUIVO + System.lineSeparator());
        ResolvedorDeSal resolvedor = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo));

        SalResolvido resolvido = resolvedor.resolver(null, null);

        assertThat(resolvido.origem()).isEqualTo(OrigemDoSal.ARQUIVO_LOCAL);
        assertThat(resolvido.sal().valor()).isEqualTo(SAL_DE_ARQUIVO);
        assertThat(resolvido.arquivo()).contains(arquivo);
    }

    @Test
    void deveGerarEGravarUmSalNovoQuandoNaoHouverNenhumaFonte(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("sub").resolve("pasta").resolve("sal");
        ResolvedorDeSal resolvedor = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo));

        SalResolvido resolvido = resolvedor.resolver(null, null);

        assertThat(resolvido.origem()).isEqualTo(OrigemDoSal.GERADO_AGORA);
        assertThat(arquivo)
                .as("o diretório que faltava é criado, e o sal fica gravado nele")
                .exists();
        assertThat(resolvido.sal().valor()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(resolvido.restricao()).isPresent();
    }

    @Test
    void oSalGeradoDeveSerLidoDeVoltaNaSubidaSeguinte(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("sal");

        SalResolvido primeira = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo))
                .resolver(null, null);
        SalResolvido segunda = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo))
                .resolver(null, null);

        assertThat(primeira.origem()).isEqualTo(OrigemDoSal.GERADO_AGORA);
        assertThat(segunda.origem()).isEqualTo(OrigemDoSal.ARQUIVO_LOCAL);
        assertThat(segunda.sal().valor())
                .as("subir duas vezes não pode trocar o sal: seria o próprio defeito que o guarda existe para pegar")
                .isEqualTo(primeira.sal().valor());
        assertThat(segunda.impressaoDigital()).isEqualTo(primeira.impressaoDigital());
    }

    @Test
    void deveTratarArquivoVazioComoAusenteEGerarUmSalNovo(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("sal");
        Files.writeString(arquivo, "   " + System.lineSeparator());

        SalResolvido resolvido = new ResolvedorDeSal(ArquivoDeSalLocal.em(arquivo))
                .resolver(null, null);

        assertThat(resolvido.origem())
                .as("arquivo truncado não vira um sal em branco recusado mais adiante")
                .isEqualTo(OrigemDoSal.GERADO_AGORA);
    }

    @Test
    void aImpressaoDigitalNaoDeveConterOSal(@TempDir Path pasta) {
        SalResolvido resolvido = new ResolvedorDeSal(ArquivoDeSalLocal.em(pasta.resolve("sal")))
                .resolver(SAL_DE_PROPRIEDADE, null);

        String impressao = resolvido.impressaoDigital().valor();

        assertThat(impressao).doesNotContain(SAL_DE_PROPRIEDADE);
        assertThat(impressao).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(resolvido.sal().toString())
                .as("nem o toString do sal devolve o valor")
                .doesNotContain(SAL_DE_PROPRIEDADE);
    }

    @Test
    void saisDiferentesDevemProduzirImpressoesDigitaisDiferentes(@TempDir Path pasta) {
        ArquivoDeSalLocal arquivo = ArquivoDeSalLocal.em(pasta.resolve("sal"));

        SalResolvido um = new ResolvedorDeSal(arquivo).resolver(SAL_DE_PROPRIEDADE, null);
        SalResolvido outro = new ResolvedorDeSal(arquivo).resolver(SAL_DE_AMBIENTE, null);

        assertThat(um.impressaoDigital()).isNotEqualTo(outro.impressaoDigital());
    }
}
