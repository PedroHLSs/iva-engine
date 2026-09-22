package br.edu.tcc.auditoria.infraestrutura.lote;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * O nome do arquivo ilegível entra no banco sem o CNPJ e sem o diretório de
 * quem rodou.
 */
class OrigemDeArquivoIlegivelTest {

    /** Escrito pelo ponto de código para o arquivo não depender de escape. */
    private static final String BARRA_INVERTIDA = String.valueOf((char) 92);

    private static final String CNPJ_DO_EMITENTE = "12345678000199";

    /** Chave fictícia montada por partes, para as posições ficarem visíveis. */
    private static final String CHAVE_FICTICIA =
            "35" + "2401" + CNPJ_DO_EMITENTE + "55" + "001" + "000000123" + "1" + "12345678" + "9";

    @Test
    void aChaveFicticiaDesteTesteDeveTerAFormaDeUmaChaveDeAcesso() {
        assertThat(CHAVE_FICTICIA).hasSize(44).containsOnlyDigits();
        assertThat(CHAVE_FICTICIA.substring(6, 20)).isEqualTo(CNPJ_DO_EMITENTE);
    }

    @Test
    void deveOcultarOCnpjDoEmitenteQueViajaDentroDaChaveNoNomeDoArquivo() {
        String limpo = OrigemDeArquivoIlegivel.semIdentificador(
                "/dados/2026-01/" + CHAVE_FICTICIA + "-nfe.xml");

        assertThat(limpo)
                .describedAs("as posições 7 a 20 da chave são o CNPJ de quem emitiu")
                .doesNotContain(CNPJ_DO_EMITENTE);
        assertThat(limpo).contains(OrigemDeArquivoIlegivel.MARCA_DO_CNPJ);
    }

    @Test
    void deveManterOQueLocalizaOArquivoSemIdentificarNinguem() {
        String limpo = OrigemDeArquivoIlegivel.semIdentificador(CHAVE_FICTICIA + "-nfe.xml");

        assertThat(limpo)
                .describedAs("UF e competência abrem a chave e não são dado de participante")
                .startsWith("352401");
        assertThat(limpo)
                .describedAs("modelo, série, número e código numérico localizam a nota")
                .contains("55001000000123112345678");
        assertThat(limpo).endsWith("-nfe.xml");
    }

    @Test
    void deveDescartarODiretorioEGuardarSoONomeDoArquivo() {
        String caminho = "C:" + BARRA_INVERTIDA + "Users" + BARRA_INVERTIDA + "fulano"
                + BARRA_INVERTIDA + "dados" + BARRA_INVERTIDA + "nota.xml";

        String limpo = OrigemDeArquivoIlegivel.semIdentificador(caminho);

        assertThat(limpo).isEqualTo("nota.xml");
        assertThat(limpo)
                .describedAs("o caminho de uma máquina real começa pelo nome de quem a usa")
                .doesNotContain("fulano");
    }

    @Test
    void deveDescartarODiretorioTambemComBarraNormal() {
        assertThat(OrigemDeArquivoIlegivel.semIdentificador("/home/fulano/dados/nota.xml"))
                .isEqualTo("nota.xml");
    }

    @Test
    void deveGuardarOCaminhoRelativoDentroDoPacote() {
        String origemDeEntrada = "C:" + BARRA_INVERTIDA + "tmp" + BARRA_INVERTIDA
                + "lote.zip!2026-01/nota.xml";

        assertThat(OrigemDeArquivoIlegivel.semIdentificador(origemDeEntrada))
                .describedAs("dentro do pacote o caminho já é relativo e distingue nomes iguais")
                .isEqualTo("2026-01/nota.xml");
    }

    @Test
    void deveDeixarPassarNomeQueNaoTemChave() {
        assertThat(OrigemDeArquivoIlegivel.semIdentificador("documento-corrompido.xml"))
                .isEqualTo("documento-corrompido.xml");
    }

    @Test
    void deveConverterAFalhaPreservandoTipoDeErroEMotivo() {
        FalhaDeLeitura falha = new FalhaDeLeitura(
                "/dados/nota.xml", "DocumentoFiscalIlegivel", "motivo fictício de teste");

        ArquivoIlegivel ilegivel = OrigemDeArquivoIlegivel.de(falha);

        assertThat(ilegivel.origem()).isEqualTo("nota.xml");
        assertThat(ilegivel.tipoDeErro()).isEqualTo("DocumentoFiscalIlegivel");
        assertThat(ilegivel.motivo()).isEqualTo("motivo fictício de teste");
    }

    /**
     * A prova de que as duas peças se encaixam: {@code ArquivoIlegivel} recusa
     * corrida de 44 dígitos, e o que este conversor produz passa por lá.
     */
    @Test
    void oQueEsteConversorProduzDeveSerAceitoPeloRegistroDaAnalise() {
        FalhaDeLeitura falha = new FalhaDeLeitura(
                "/dados/" + CHAVE_FICTICIA + "-nfe.xml",
                "DocumentoFiscalIlegivel",
                "motivo fictício de teste");

        assertThatCode(() -> OrigemDeArquivoIlegivel.de(falha)).doesNotThrowAnyException();
    }
}
