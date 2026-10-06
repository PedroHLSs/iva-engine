package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.regras.CampoDoItem;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ContextoNormativoFalso;
import br.edu.tcc.auditoria.dominio.regras.RegraCamposObrigatoriosPreenchidos;
import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D015 (03/10/2026): os seis campos do grupo gRed chegam do XML ao ItemDocumento como vieram, e a R07 passa a poder cobrá-los. Usa as notas fictícias de src/test/resources/documentos: uma com gRed nos três tributos, outra sem gRed nenhum.
class ReducaoDeAliquotaNoItemTest {

    private static final String COM_REDUCAO = "nfe-item-completo-reducao-60.xml";

    // Fora de /documentos de propósito: tem a mesma chave fictícia das notas de lá, e os testes que leem aquela pasta inteira a contariam como documento repetido.
    private static final String REDUCAO_DISTINTA = "/documentos-reducao/nfe-reducao-distinta-por-tributo.xml";

    private final LeitorDocumentoFiscal leitor = DocumentoDeTeste.leitor();
    private final NormalizadorDocumento normalizador = DocumentoDeTeste.normalizador();

    @Test
    void deveLerOsSeisCamposDoGrupoDeReducaoComAEscalaDeclarada() throws IOException {
        ItemDocumento item = primeiroItem(COM_REDUCAO);

        assertThat(item.reducaoAliquotaIbsUf()).hasValueSatisfying(valor -> assertThat(valor).isEqualTo(new BigDecimal("60.0000")));
        assertThat(item.aliquotaEfetivaIbsUf()).hasValueSatisfying(valor -> assertThat(valor).isEqualTo(new BigDecimal("3.9960")));
        assertThat(item.reducaoAliquotaIbsMunicipal()).hasValueSatisfying(valor -> assertThat(valor).isEqualTo(new BigDecimal("60.0000")));
        assertThat(item.aliquotaEfetivaIbsMunicipal()).hasValueSatisfying(valor -> assertThat(valor).isEqualTo(new BigDecimal("3.5520")));
        assertThat(item.reducaoAliquotaCbs()).hasValueSatisfying(valor -> assertThat(valor).isEqualTo(new BigDecimal("60.0000")));
        assertThat(item.aliquotaEfetivaCbs()).hasValueSatisfying(valor -> assertThat(valor).isEqualTo(new BigDecimal("3.1080")));
    }

    // A nota de teste acima declara 60,0000 nos três grupos, e com ela trocar o grupo de onde se lê a redução passaria despercebido — foi o que a sabotagem mostrou. Esta nota fictícia, derivada daquela, declara um valor diferente por tributo.
    @Test
    void cadaTributoDeveSerLidoDoProprioGrupo() throws IOException {
        ItemDocumento item;
        try (InputStream conteudo = getClass().getResourceAsStream(REDUCAO_DISTINTA)) {
            item = normalizador.normalizar(leitor.ler(conteudo), new DescricoesDeProdutoEmMemoria())
                    .itensOrdenados().get(0);
        }

        assertThat(item.reducaoAliquotaIbsUf()).contains(new BigDecimal("11.1100"));
        assertThat(item.aliquotaEfetivaIbsUf()).contains(new BigDecimal("1.1100"));
        assertThat(item.reducaoAliquotaIbsMunicipal()).contains(new BigDecimal("22.2200"));
        assertThat(item.aliquotaEfetivaIbsMunicipal()).contains(new BigDecimal("2.2200"));
        assertThat(item.reducaoAliquotaCbs()).contains(new BigDecimal("33.3300"));
        assertThat(item.aliquotaEfetivaCbs()).contains(new BigDecimal("3.3300"));
    }

    @Test
    void grupoAusenteDeveVirarOptionalVazioNuncaZero() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COMPLETO);

        assertThat(item.reducaoAliquotaIbsUf()).isEmpty();
        assertThat(item.aliquotaEfetivaIbsUf()).isEmpty();
        assertThat(item.reducaoAliquotaIbsMunicipal()).isEmpty();
        assertThat(item.aliquotaEfetivaIbsMunicipal()).isEmpty();
        assertThat(item.reducaoAliquotaCbs()).isEmpty();
        assertThat(item.aliquotaEfetivaCbs()).isEmpty();
    }

    // O caso que a R07 1.0.0 nunca pôde apontar: o catálogo exige a redução, e a nota veio sem o grupo.
    @Test
    void aR07DeveApontarNotaSemOGrupoQuandoOCatalogoOExige() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COMPLETO);

        assertThat(r07(item).resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    @Test
    void aR07DeveDarConformeQuandoOGrupoExigidoVeio() throws IOException {
        ItemDocumento item = primeiroItem(COM_REDUCAO);

        assertThat(r07(item).resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Método auxiliar que roda a R07 com um catálogo fictício que exige a redução de alíquota da CBS para o código do item.
    private static br.edu.tcc.auditoria.dominio.regras.Avaliacao r07(ItemDocumento item) {
        String codigo = item.codigoClassificacaoTributaria().orElseThrow().valor();
        ClassificacaoTributaria exigindoReducao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(codigo),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                "DISPOSITIVO FICTICIO PARA TESTE",
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(CampoDoItem.REDUCAO_ALIQUOTA_CBS.nomeNoCatalogo())),
                CenarioFicticio.procedencia());
        return new RegraCamposObrigatoriosPreenchidos().avaliar(
                item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio().com(exigindoReducao));
    }

    // Método auxiliar que lê e normaliza a nota fictícia e devolve o primeiro item.
    private ItemDocumento primeiroItem(String nomeDoDocumento) throws IOException {
        try (InputStream conteudo = DocumentoDeTeste.abrir(nomeDoDocumento)) {
            DocumentoComItens documento = normalizador.normalizar(leitor.ler(conteudo), new DescricoesDeProdutoEmMemoria());
            return documento.itensOrdenados().get(0);
        }
    }
}
