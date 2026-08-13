package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportadoresDeCatalogoCsvTest {

    private final ImportadorItemAnexoCsv importadorDeAnexos = new ImportadorItemAnexoCsv();
    private final ImportadorRegistroNcmCsv importadorDeNcm = new ImportadorRegistroNcmCsv();
    private final ImportadorAliquotaVigenteCsv importadorDeAliquotas = new ImportadorAliquotaVigenteCsv();

    @Test
    void deveImportarVinculosEntreNcmEAnexo() throws IOException {
        List<ItemAnexo> importados;
        try (Reader origem = ArquivoDeTeste.csv("item-anexo-ficticio.csv")) {
            importados = importadorDeAnexos.importar(origem);
        }

        assertThat(importados).hasSize(3);
        assertThat(importados.get(0).ncm()).isEqualTo(new Ncm("00000000"));
        assertThat(importados.get(0).identificadorDoAnexo().valor()).isEqualTo("ANEXO-XX");
        assertThat(importados.get(0).tipoDeTratamento()).isEqualTo("TRATAMENTO-XX");
        assertThat(importados.get(1).vigenciaFim()).isEmpty();
    }

    @Test
    void deveCarregarOMesmoNcmEmDoisAnexosSemAcusarSobreposicao() throws IOException {
        List<ItemAnexo> importados;
        try (Reader origem = ArquivoDeTeste.csv("item-anexo-ficticio.csv")) {
            importados = importadorDeAnexos.importar(origem);
        }

        RepositorioItemAnexoEmMemoria repositorio = new RepositorioItemAnexoEmMemoria(importados);

        assertThat(repositorio.buscarVigentesEm(new Ncm("00000000"), LocalDate.of(1900, 1, 1))).hasSize(2);
    }

    @Test
    void deveImportarRegistrosDeNcm() throws IOException {
        List<RegistroNcm> importados;
        try (Reader origem = ArquivoDeTeste.csv("registro-ncm-ficticio.csv")) {
            importados = importadorDeNcm.importar(origem);
        }

        assertThat(importados).hasSize(3);
        assertThat(importados.get(0).descricao()).isEqualTo("Descricao ficticia de teste");
    }

    @Test
    void deveResolverAVersaoCertaDoNcmConformeADataConsultada() throws IOException {
        List<RegistroNcm> importados;
        try (Reader origem = ArquivoDeTeste.csv("registro-ncm-ficticio.csv")) {
            importados = importadorDeNcm.importar(origem);
        }
        RepositorioNcmEmMemoria repositorio = new RepositorioNcmEmMemoria(importados);
        Ncm ncm = new Ncm("00000000");

        assertThat(repositorio.buscarVigenteEm(ncm, LocalDate.of(1900, 1, 1)).orElseThrow().descricao())
                .isEqualTo("Descricao ficticia de teste");
        assertThat(repositorio.buscarVigenteEm(ncm, LocalDate.of(1900, 7, 1)).orElseThrow().descricao())
                .isEqualTo("Descricao ficticia de teste revisada");
        assertThat(repositorio.buscarVigenteEm(ncm, LocalDate.of(1899, 12, 31))).isEmpty();
    }

    @Test
    void deveImportarAliquotasComVirgulaComoSeparadorDecimal() throws IOException {
        List<AliquotaVigente> importadas;
        try (Reader origem = ArquivoDeTeste.csv("aliquota-vigente-ficticia.csv")) {
            importadas = importadorDeAliquotas.importar(origem);
        }

        assertThat(importadas).hasSize(4);
        assertThat(importadas.get(0).tributo()).isEqualTo(Tributo.IBS_UF);
        assertThat(importadas.get(0).percentual()).isEqualByComparingTo("99.99");
        assertThat(importadas.get(0).abrangencia().valor()).isEqualTo("ABRANGENCIA-XX");
    }

    @Test
    void deveResolverAAliquotaCertaConformeADataConsultada() throws IOException {
        List<AliquotaVigente> importadas;
        try (Reader origem = ArquivoDeTeste.csv("aliquota-vigente-ficticia.csv")) {
            importadas = importadorDeAliquotas.importar(origem);
        }
        RepositorioAliquotaEmMemoria repositorio = new RepositorioAliquotaEmMemoria(importadas);
        var abrangencia = importadas.get(0).abrangencia();

        assertThat(repositorio.buscarVigenteEm(Tributo.IBS_UF, abrangencia, LocalDate.of(1900, 1, 1))
                .orElseThrow().percentual()).isEqualByComparingTo("99.99");
        assertThat(repositorio.buscarVigenteEm(Tributo.IBS_UF, abrangencia, LocalDate.of(1900, 7, 1))
                .orElseThrow().percentual()).isEqualByComparingTo("88.88");
        assertThat(repositorio.buscarVigenteEm(Tributo.IBS_UF, abrangencia, LocalDate.of(1899, 12, 31)))
                .isEmpty();
    }

    @Test
    void deveRejeitarTributoDesconhecidoIndicandoALinha() {
        String csv = """
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa
                TRIBUTO_INEXISTENTE;99,99;ABRANGENCIA-XX;1900-01-01;;FONTE FICTICIA v0.0
                """;

        assertThatThrownBy(() -> importadorDeAliquotas.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("TRIBUTO_INEXISTENTE");
    }

    @Test
    void deveRejeitarAliquotaSemPercentualIndicandoALinha() {
        String csv = """
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa
                CBS;;ABRANGENCIA-XX;1900-01-01;;FONTE FICTICIA v0.0
                """;

        assertThatThrownBy(() -> importadorDeAliquotas.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("percentual");
    }

    @Test
    void deveRejeitarItemDeAnexoSemFonteNormativaIndicandoALinha() {
        String csv = """
                ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa
                00000000;ANEXO-XX;TRATAMENTO-XX;1900-01-01;;
                """;

        assertThatThrownBy(() -> importadorDeAnexos.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("fonteNormativa");
    }

    @Test
    void deveRejeitarRegistroDeNcmSemVigenciaInicioIndicandoALinha() {
        String csv = """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa
                00000000;Descricao ficticia;;;FONTE FICTICIA v0.0
                """;

        assertThatThrownBy(() -> importadorDeNcm.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("vigenciaInicio");
    }

    @Test
    void deveAcrescentarONumeroDaLinhaQuandoODominioRecusaONcm() {
        String csv = """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa
                123;Descricao ficticia;1900-01-01;;FONTE FICTICIA v0.0
                """;

        assertThatThrownBy(() -> importadorDeNcm.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("8 dígitos");
    }
}
