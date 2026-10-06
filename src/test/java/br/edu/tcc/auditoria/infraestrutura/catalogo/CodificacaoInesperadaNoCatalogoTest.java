package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// D024 (04/10/2026): o mesmo arquivo do catálogo em Windows-1252 é recusado do mesmo jeito pela pasta (CLI) e pelo envio (web), com a mensagem dizendo que a codificação esperada é UTF-8. Antes, a pasta caía em MalformedInputException e o envio era aceito, gravando "DESCRI?O FICT?CIA". Todos os valores são fictícios.
class CodificacaoInesperadaNoCatalogoTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String VIGENCIA = "1900-01-01;1900-12-31";
    private static final String DESCRICAO = "DESCRIÇÃO FICTÍCIA";
    private static final String NCM = "registro-ncm.csv";

    @TempDir
    private Path diretorio;

    // Controle: o mesmo catálogo em UTF-8 importa pelos dois caminhos, com o texto acentuado intacto.
    @Test
    void emUtf8ODeveImportarPelosDoisCaminhosComOAcentoIntacto() throws IOException {
        Map<String, byte[]> arquivos = catalogo(StandardCharsets.UTF_8);

        assertThat(descricaoDoNcm(porPasta(arquivos))).isEqualTo(DESCRICAO);
        assertThat(descricaoDoNcm(porEnvio(arquivos))).isEqualTo(DESCRICAO);
    }

    @Test
    void emWindows1252APastaDeveRecusarDizendoACodificacaoEsperada() {
        Map<String, byte[]> arquivos = catalogo(WINDOWS_1252);

        assertThatThrownBy(() -> porPasta(arquivos))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining(NCM)
                .hasMessageContaining("UTF-8")
                .hasMessageContaining("linha 2");
    }

    @Test
    void emWindows1252OEnvioDeveRecusarDizendoACodificacaoEsperada() {
        Map<String, byte[]> arquivos = catalogo(WINDOWS_1252);

        assertThatThrownBy(() -> porEnvio(arquivos))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining(NCM)
                .hasMessageContaining("UTF-8")
                .hasMessageContaining("linha 2");
    }

    // A pergunta do pedido: a mesma recusa, linha por linha, nos dois caminhos.
    @Test
    void osDoisCaminhosDevemDarAMesmaRecusa() {
        Map<String, byte[]> arquivos = catalogo(WINDOWS_1252);

        CargaRecusada pelaPasta = catchThrowableOfType(() -> porPasta(arquivos), CargaRecusada.class);
        CargaRecusada peloEnvio = catchThrowableOfType(() -> porEnvio(arquivos), CargaRecusada.class);

        assertThat(pelaPasta).as("recusa pela pasta").isNotNull();
        assertThat(peloEnvio).as("recusa pelo envio").isNotNull();
        assertThat(peloEnvio.recusadas()).isEqualTo(pelaPasta.recusadas());
        assertThat(peloEnvio.getMessage()).isEqualTo(pelaPasta.getMessage());
    }

    // A edição de carga pela web também lê pelo envio, e também recusa.
    @Test
    void aEdicaoPeloEnvioDeveRecusarDoMesmoJeito() {
        Map<String, byte[]> soONcm = Map.of(NCM, catalogo(WINDOWS_1252).get(NCM));

        assertThatThrownBy(() -> LeitorDeCatalogoEmCsv.lerSubstituicao(FontesDoCatalogo.emMemoria(soONcm)))
                .isInstanceOf(CargaRecusada.class)
                .hasMessageContaining(NCM)
                .hasMessageContaining("UTF-8");
    }

    // Recusa é a carga inteira, e os outros arquivos continuam conferidos: o outro problema aparece junto.
    @Test
    void aRecusaDeCodificacaoNaoDeveEsconderOutroProblemaDaCarga() {
        Map<String, byte[]> arquivos = catalogo(WINDOWS_1252);
        arquivos.remove("aliquota-vigente.csv");

        CargaRecusada recusa = catchThrowableOfType(() -> porEnvio(arquivos), CargaRecusada.class);

        assertThat(recusa).isNotNull();
        assertThat(recusa.recusadas()).extracting(LinhaRecusada::arquivo)
                .contains(NCM, "aliquota-vigente.csv");
    }

    // O anexos-declarados.csv é opcional e é aberto também só para saber se veio: fora de UTF-8, ele veio, e a recusa entra na lista da carga como as outras.
    @Test
    void anexosDeclaradosForaDeUtf8DevemEntrarNaListaDaCarga() {
        Map<String, byte[]> arquivos = catalogo(StandardCharsets.UTF_8);
        arquivos.put("anexos-declarados.csv", """
                identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                ANEXO-XX;NCM;1900-01-01;;FONTE FICTÍCIA;FICTICIO
                """.getBytes(WINDOWS_1252));
        arquivos.remove("aliquota-vigente.csv");

        CargaRecusada recusa = catchThrowableOfType(() -> porEnvio(arquivos), CargaRecusada.class);

        assertThat(recusa).as("a recusa é da carga inteira, com a lista").isNotNull();
        assertThat(recusa.recusadas()).extracting(LinhaRecusada::arquivo)
                .contains("anexos-declarados.csv", "aliquota-vigente.csv");
        assertThat(recusa.getMessage()).contains("UTF-8").contains("linha 2");
    }

    private CargaDeCatalogo porPasta(Map<String, byte[]> arquivos) throws IOException {
        for (Map.Entry<String, byte[]> arquivo : arquivos.entrySet()) {
            Files.write(diretorio.resolve(arquivo.getKey()), arquivo.getValue());
        }
        return LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia");
    }

    private static CargaDeCatalogo porEnvio(Map<String, byte[]> arquivos) throws IOException {
        return LeitorDeCatalogoEmCsv.ler(FontesDoCatalogo.emMemoria(arquivos), "carga-ficticia");
    }

    private static String descricaoDoNcm(CargaDeCatalogo carga) {
        List<RegistroNcm> registros = carga.registrosDeNcm();
        assertThat(registros).hasSize(1);
        return registros.get(0).descricao();
    }

    // Os cinco arquivos em UTF-8, menos o de NCM, escrito na codificação pedida.
    private static Map<String, byte[]> catalogo(Charset codificacaoDoNcm) {
        Map<String, byte[]> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", utf8("""
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FONTE FICTICIA v0.0;FICTICIO
                NCM;%1$s;FONTE FICTICIA v0.0;FICTICIO
                ITEM_ANEXO;%1$s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        arquivos.put("classificacao-tributaria.csv", utf8("""
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX000;AAA;Dispositivo ficticio;false;;NENHUM;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        arquivos.put(NCM, """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;%s;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(DESCRICAO, VIGENCIA).getBytes(codificacaoDoNcm));
        arquivos.put("item-anexo.csv", utf8("""
                ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        arquivos.put("aliquota-vigente.csv", utf8("""
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CBS;99,99;ABRANGENCIA-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA)));
        return arquivos;
    }

    private static byte[] utf8(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }
}
