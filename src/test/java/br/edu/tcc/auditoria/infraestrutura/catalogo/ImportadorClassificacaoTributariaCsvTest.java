package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportadorClassificacaoTributariaCsvTest {

    private final ImportadorClassificacaoTributariaCsv importador = new ImportadorClassificacaoTributariaCsv();

    @Test
    void deveImportarAsLinhasIgnorandoComentariosELinhasEmBranco() throws IOException {
        List<ClassificacaoTributaria> importadas = importar("classificacao-tributaria-ficticia.csv");

        assertThat(importadas).hasSize(3);
    }

    @Test
    void deveLerAsColunasComunsDeVigenciaEFonte() throws IOException {
        ClassificacaoTributaria primeira = importar("classificacao-tributaria-ficticia.csv").get(0);

        assertThat(primeira.vigenciaInicio()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(primeira.vigenciaFim()).contains(LocalDate.of(1900, 6, 30));
        assertThat(primeira.fonteNormativa()).isEqualTo("FONTE FICTICIA v0.0");
    }

    @Test
    void deveTratarFimDeVigenciaEmBrancoComoVigenciaAberta() throws IOException {
        ClassificacaoTributaria segunda = importar("classificacao-tributaria-ficticia.csv").get(1);

        assertThat(segunda.vigenciaFim()).isEmpty();
        assertThat(segunda.vigencia().estaAberta()).isTrue();
    }

    @Test
    void deveLerListaDeCstsSeparadaPorBarraVertical() throws IOException {
        ClassificacaoTributaria primeira = importar("classificacao-tributaria-ficticia.csv").get(0);

        assertThat(primeira.cstsCompativeis())
                .containsExactlyInAnyOrder(new CodigoCst("AAA"), new CodigoCst("BBB"));
    }

    @Test
    void deveLerListaDeCamposObrigatoriosCondicionados() throws IOException {
        ClassificacaoTributaria segunda = importar("classificacao-tributaria-ficticia.csv").get(1);

        assertThat(segunda.camposObrigatoriosCondicionados())
                .containsExactly("campoFicticioUm", "campoFicticioDois");
    }

    @Test
    void deveDistinguirReducaoAusenteDeReducaoInformada() throws IOException {
        List<ClassificacaoTributaria> importadas = importar("classificacao-tributaria-ficticia.csv");

        assertThat(importadas.get(0).percentualReducao()).isEmpty();
        assertThat(importadas.get(1).percentualReducao()).isPresent();
        assertThat(importadas.get(1).percentualReducao().orElseThrow()).isEqualByComparingTo("99.99");
    }

    @Test
    void deveLerIndicadorDeBeneficio() throws IOException {
        List<ClassificacaoTributaria> importadas = importar("classificacao-tributaria-ficticia.csv");

        assertThat(importadas.get(0).indicadorDeBeneficio()).isFalse();
        assertThat(importadas.get(1).indicadorDeBeneficio()).isTrue();
    }

    @Test
    void deveLerCampoEntreAspasComSeparadorEAspasNoMeio() throws IOException {
        ClassificacaoTributaria terceira = importar("classificacao-tributaria-ficticia.csv").get(2);

        assertThat(terceira.dispositivoLegal())
                .isEqualTo("Dispositivo ficticio com ; separador e \"aspas\" no meio");
    }

    @Test
    void deveRejeitarLinhaSemFonteNormativaIndicandoALinha() {
        assertThatThrownBy(() -> importar("classificacao-tributaria-sem-fonte-normativa.csv"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 5")
                .hasMessageContaining("fonteNormativa");
    }

    @Test
    void deveRejeitarLinhaSemVigenciaInicioIndicandoALinha() {
        assertThatThrownBy(() -> importar("classificacao-tributaria-sem-vigencia-inicio.csv"))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 5")
                .hasMessageContaining("vigenciaInicio");
    }

    @Test
    void deveAcusarNaCargaOArquivoComVigenciasSobrepostas() throws IOException {
        // A importação em si passa: uma linha isolada não sabe da outra.
        // Quem recusa é a carga do repositório, onde a série é montada.
        List<ClassificacaoTributaria> importadas =
                importar("classificacao-tributaria-vigencias-sobrepostas.csv");

        assertThat(importadas).hasSize(2);
        assertThatThrownBy(() -> new RepositorioClassificacaoTributariaEmMemoria(importadas))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("sobrepostas");
    }

    @Test
    void deveRejeitarLinhaComCodigoEmBrancoIndicandoALinha() {
        String csv = """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa
                ;AAA;Dispositivo ficticio;false;;;1900-01-01;;FONTE FICTICIA v0.0
                """;

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("codigo");
    }

    @Test
    void deveAcrescentarONumeroDaLinhaQuandoODominioRecusaOValor() {
        String csv = """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa
                XXX001;AAA;Dispositivo ficticio;false;;;1900-01-01;1899-12-31;FONTE FICTICIA v0.0
                """;

        // Fim anterior ao início: quem recusa é o domínio, mas a mensagem precisa
        // dizer onde corrigir o arquivo.
        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2");
    }

    @Test
    void deveRejeitarIndicadorDeBeneficioQueNaoSejaVerdadeiroOuFalso() {
        String csv = """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa
                XXX001;AAA;Dispositivo ficticio;talvez;;;1900-01-01;;FONTE FICTICIA v0.0
                """;

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("indicadorDeBeneficio");
    }

    private List<ClassificacaoTributaria> importar(String arquivo) throws IOException {
        try (Reader origem = ArquivoDeTeste.csv(arquivo)) {
            return importador.importar(origem).registros();
        }
    }
}
