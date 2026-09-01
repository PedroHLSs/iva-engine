package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.dominio.Cfop;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Uf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorDocumentoTest {

    private final LeitorDocumentoFiscal leitor = DocumentoDeTeste.leitor();
    private final NormalizadorDocumento normalizador = DocumentoDeTeste.normalizador();

    @Test
    void deveNormalizarIdentificacaoDoDocumento() throws IOException {
        Documento documento = normalizar(DocumentoDeTeste.ITEM_COMPLETO).documento();

        assertThat(documento.chaveAcesso().valor())
                .as("a chave sai do atributo Id, sem o prefixo \"NFe\"")
                .isEqualTo("11111111111111111111111111111111111111111111");
        assertThat(documento.modelo()).isEqualTo("55");
        assertThat(documento.serie()).isEqualTo("999");
        assertThat(documento.numero()).isEqualTo("999999999");
        assertThat(documento.dataEmissao()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(documento.ufEmitente()).isEqualTo(Uf.MG);
        assertThat(documento.ufDestinatario()).contains(Uf.SP);
        assertThat(documento.crtEmitente()).contains("3");
        assertThat(documento.indicadorDestinatario()).contains("1");
        assertThat(documento.ehInterestadual()).contains(true);
    }

    @Test
    void deveNormalizarItemComTodosOsCamposDeIbsCbsPreenchidos() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COMPLETO);

        assertThat(item.numeroItem()).isEqualTo(1);
        assertThat(item.ncm()).contains(new Ncm("00000000"));
        assertThat(item.cfop()).contains(new Cfop("5999"));
        assertThat(item.valorItem()).isEqualByComparingTo("99.99");
        assertThat(item.codigoClassificacaoTributaria())
                .contains(new CodigoClassificacaoTributaria("999999"));
        assertThat(item.baseCalculoIbs()).contains(new BigDecimal("99.99"));
        assertThat(item.aliquotaIbsUf()).contains(new BigDecimal("9.9900"));
        assertThat(item.valorIbsUf()).contains(new BigDecimal("9.99"));
        assertThat(item.aliquotaIbsMunicipal()).contains(new BigDecimal("8.8800"));
        assertThat(item.valorIbsMunicipal()).contains(new BigDecimal("8.88"));
        assertThat(item.aliquotaCbs()).contains(new BigDecimal("7.7700"));
        assertThat(item.valorCbs()).contains(new BigDecimal("7.77"));
    }

    /**
     * O grupo IBSCBS do leiaute traz um CST e uma base de cálculo só, válidos
     * para os dois tributos, e o domínio tem campo separado para cada um.
     */
    @Test
    void deveRepetirNosDoisTributosOCstEABaseDeclaradosUmaVezSo() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COMPLETO);

        assertThat(item.cstIbs()).contains(new CodigoCst("999"));
        assertThat(item.cstCbs()).isEqualTo(item.cstIbs());
        assertThat(item.baseCalculoCbs()).isEqualTo(item.baseCalculoIbs());
    }

    @Test
    void devePreservarAEscalaDeclaradaNoDocumento() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COMPLETO);

        assertThat(item.baseCalculoIbs().orElseThrow().scale())
                .as("\"99.99\" e \"99.9900\" são registros diferentes do mesmo número")
                .isEqualTo(2);
        assertThat(item.aliquotaIbsUf().orElseThrow().scale()).isEqualTo(4);
    }

    @Test
    void deveDeixarVazioOCampoDeIbsCbsQueODocumentoNaoDeclarou() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COM_CAMPOS_AUSENTES);

        assertThat(item.cstIbs()).contains(new CodigoCst("999"));
        assertThat(item.codigoClassificacaoTributaria())
                .contains(new CodigoClassificacaoTributaria("999999"));

        assertThat(item.baseCalculoIbs()).isEmpty();
        assertThat(item.baseCalculoCbs()).isEmpty();
        assertThat(item.aliquotaIbsUf()).isEmpty();
        assertThat(item.aliquotaIbsMunicipal()).isEmpty();
        assertThat(item.aliquotaCbs()).isEmpty();
        assertThat(item.valorIbsUf()).isEmpty();
        assertThat(item.valorIbsMunicipal()).isEmpty();
        assertThat(item.valorCbs()).isEmpty();
    }

    @Test
    void naoDeveTrocarCampoAusentePorZero() throws IOException {
        ItemDocumento item = primeiroItem(DocumentoDeTeste.ITEM_COM_CAMPOS_AUSENTES);

        assertThat(item.baseCalculoIbs())
                .as("campo não declarado é ausência de informação, não é informação de valor zero")
                .isNotEqualTo(Optional.of(BigDecimal.ZERO))
                .isEmpty();
    }

    @Test
    void deveDeixarVazioOEnderecoDoDestinatarioQueODocumentoNaoDeclarou() throws IOException {
        Documento documento = normalizar(DocumentoDeTeste.ITEM_COM_CAMPOS_AUSENTES).documento();

        assertThat(documento.identificadorDestinatarioPseudonimizado())
                .as("o destinatário existe, e por isso tem pseudônimo")
                .isPresent();
        assertThat(documento.ufDestinatario())
                .as("o destinatário existe e não declarou endereço")
                .isEmpty();
        assertThat(documento.ehInterestadual())
                .as("sem UF de destino não há como afirmar nem negar interestadualidade")
                .isEmpty();
    }

    @Test
    void deveNormalizarNfceSemDestinatarioIdentificado() throws IOException {
        DocumentoComItens normalizado = normalizar(DocumentoDeTeste.NFCE_SEM_DESTINATARIO);
        Documento documento = normalizado.documento();

        assertThat(documento.modelo()).isEqualTo("65");
        assertThat(documento.identificadorDestinatarioPseudonimizado()).isEmpty();
        assertThat(documento.ufDestinatario()).isEmpty();
        assertThat(documento.indicadorDestinatario()).isEmpty();
        assertThat(documento.identificadorEmitentePseudonimizado()).isNotNull();
        assertThat(normalizado.itens()).hasSize(1);
    }

    @Test
    void deveNormalizarTodosOsItensDoDocumento() throws IOException {
        DocumentoComItens normalizado = normalizar(DocumentoDeTeste.MULTIPLOS_ITENS);

        assertThat(normalizado.itensOrdenados())
                .extracting(ItemDocumento::numeroItem)
                .containsExactly(1, 2, 3);
    }

    @Test
    void deveNormalizarCadaItemPeloQueEleMesmoDeclarou() throws IOException {
        var itens = normalizar(DocumentoDeTeste.MULTIPLOS_ITENS).itensOrdenados();

        assertThat(itens.get(0).valorCbs()).contains(new BigDecimal("3.33"));
        assertThat(itens.get(1).cstIbs()).contains(new CodigoCst("888"));
        assertThat(itens.get(1).valorCbs()).isEmpty();
        assertThat(itens.get(2).semNenhumCampoDeIbsCbs())
                .as("o terceiro item não declarou grupo de IBS/CBS nenhum")
                .isTrue();
    }

    @Test
    void deveLerODestinatarioPessoaFisica() throws IOException {
        Documento documento = normalizar(DocumentoDeTeste.MULTIPLOS_ITENS).documento();

        assertThat(documento.identificadorDestinatarioPseudonimizado()).isPresent();
        assertThat(documento.indicadorDestinatario()).contains("9");
        assertThat(documento.ufDestinatario()).contains(Uf.SP);
    }

    private DocumentoComItens normalizar(String nomeDoDocumento) throws IOException {
        try (InputStream conteudo = DocumentoDeTeste.abrir(nomeDoDocumento)) {
            return normalizador.normalizar(leitor.ler(conteudo));
        }
    }

    private ItemDocumento primeiroItem(String nomeDoDocumento) throws IOException {
        return normalizar(nomeDoDocumento).itensOrdenados().get(0);
    }
}
