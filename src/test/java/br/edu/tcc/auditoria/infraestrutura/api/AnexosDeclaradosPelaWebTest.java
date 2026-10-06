package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.AcervoDeCargasEmMemoriaParaTeste;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.FontesDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// D026 (04/10/2026), decisão D-c: o anexos-declarados.csv tem de entrar pela web, na importação e na edição. Até essa data o controlador só aceitava os cinco nomes de arquivosEsperados(), e carga com código que admite anexo só importava pela CLI. O pedido passa pelo despacho HTTP do Spring MVC, sem banco: o defeito é do controlador. Valores fictícios.
class AnexosDeclaradosPelaWebTest {

    private static final String VIGENCIA = "1900-01-01;1900-12-31;FONTE FICTICIA v0.0";
    private static final String DADOS = "vigenciaInicio;vigenciaFim;fonteNormativa;natureza";
    private static final String ANEXOS_DECLARADOS = """
            identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
            ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
            """;

    private AcervoDeCargasEmMemoriaParaTeste acervo;
    private MockMvc web;

    @BeforeEach
    void preparar() {
        acervo = new AcervoDeCargasEmMemoriaParaTeste();
        ServicoDeCargas cargas = new ServicoDeCargas(acervo, new ServicoDeImportacaoDeCatalogo(acervo));
        web = MockMvcBuilders.standaloneSetup(new ControladorDeCargas(cargas))
                .setControllerAdvice(new TratadorDeErrosDaIdentidade(), new TratadorDeErrosDaApi())
                .build();
    }

    @Test
    void aImportacaoPelaWebDeveAceitarOsAnexosDeclarados() throws Exception {
        Map<String, String> arquivos = catalogo();
        arquivos.put("anexos-declarados.csv", ANEXOS_DECLARADOS);

        MvcResult resposta = executar(enviar(MockMvcRequestBuilders.multipart("/api/cargas"), arquivos)
                .param("versao", "carga-com-anexos"));

        assertThat(resposta.getResponse().getStatus())
                .describedAs(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(201);
        assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"anexosDeclarados\":1");
        assertThat(acervo.conteudo("carga-com-anexos").orElseThrow().cobertura().anexosDeclarados()).hasSize(1);
    }

    @Test
    void aEdicaoPelaWebDeveAceitarOsAnexosDeclarados() throws Exception {
        Map<String, String> arquivos = catalogo();
        arquivos.put("anexos-declarados.csv", ANEXOS_DECLARADOS);
        acervo.salvar(LeitorDeCatalogoEmCsv.ler(FontesDoCatalogo.emMemoria(bytes(arquivos)), "carga-a"));

        MvcResult resposta = executar(enviar(
                MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/cargas/carga-a"),
                Map.of("anexos-declarados.csv", ANEXOS_DECLARADOS
                        + "ANEXO-YY;NBS;;;FONTE FICTICIA v0.0;FICTICIO\n"))
                .param("efeitoEsperado", "ALTERAR_RASCUNHO"));

        assertThat(resposta.getResponse().getStatus())
                .describedAs(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);
        assertThat(acervo.conteudo("carga-a").orElseThrow().cobertura().anexosDeclarados()).hasSize(2);
    }

    // Os cinco arquivos obrigatórios; o código fictício admite o ANEXO-XX.
    private static Map<String, String> catalogo() {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FICTICIO
                NCM;%1$s;FICTICIO
                ITEM_ANEXO;%1$s;FICTICIO
                """.formatted(VIGENCIA));
        arquivos.put("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;%s;anexosAdmitidos
                XXX000;AAA;Dispositivo ficticio;true;0;;%s;FICTICIO;ANEXO-XX
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("registro-ncm.csv", """
                ncm;descricao;%s
                00000000;Descricao ficticia;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;%s
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        arquivos.put("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;%s
                CBS;99,99;ABRANGENCIA-XX;%s;FICTICIO
                """.formatted(DADOS, VIGENCIA));
        return arquivos;
    }

    private static MockMultipartHttpServletRequestBuilder enviar(
            MockMultipartHttpServletRequestBuilder pedido, Map<String, String> arquivos) {
        arquivos.forEach((nome, conteudo) -> pedido.file(
                new MockMultipartFile("arquivos", nome, "text/csv", conteudo.getBytes(StandardCharsets.UTF_8))));
        return pedido;
    }

    private MvcResult executar(MockHttpServletRequestBuilder pedido) throws Exception {
        return web.perform(pedido).andReturn();
    }

    private static Map<String, byte[]> bytes(Map<String, String> arquivos) {
        Map<String, byte[]> porNome = new LinkedHashMap<>();
        arquivos.forEach((nome, conteudo) -> porNome.put(nome, conteudo.getBytes(StandardCharsets.UTF_8)));
        return porNome;
    }
}
