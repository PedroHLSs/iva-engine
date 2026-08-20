package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TNFe;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TNfeProc;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * Lê o XML de uma NF-e ou NFC-e e devolve o objeto de leiaute correspondente.
 *
 * <p>Não interpreta nada: a saída é a classe gerada pelo JAXB a partir do XSD
 * oficial, com os campos exatamente como vieram no arquivo. Traduzir isso para o
 * modelo de domínio é tarefa do {@link NormalizadorDocumento}.</p>
 *
 * <h2>As duas raízes aceitas</h2>
 *
 * <p>Documento arquivado depois da autorização vem embrulhado em
 * {@code <nfeProc>}, junto do protocolo; documento exportado antes disso vem
 * como {@code <NFe>} puro. Os dois são aceitos, e nos dois casos o que sai é o
 * {@code TNFe}. O protocolo de autorização é descartado: a auditoria de
 * coerência de IBS/CBS olha o que foi declarado, e o protocolo não diz nada
 * sobre isso.</p>
 *
 * <p>Qualquer outra raiz é recusada, mesmo que o XML esteja bem formado. Isso
 * evita que um arquivo de outro tipo — um evento, um retorno de consulta, um XML
 * qualquer — seja lido como se fosse um documento vazio.</p>
 *
 * <h2>Entrada não confiável</h2>
 *
 * <p>O leitor processa arquivos de terceiros e por isso desliga DTD e entidades
 * externas. Sem isso, um XML com {@code <!DOCTYPE>} apontando para um arquivo
 * local ou para uma URL faria o processo lê-lo em nome de quem rodou a
 * auditoria.</p>
 *
 * <p>A instância é reutilizável e o {@code JAXBContext} é criado uma vez só, que
 * é a parte cara. Cada leitura cria o seu próprio {@code Unmarshaller}, que não
 * é seguro para uso concorrente.</p>
 */
public final class LeitorDocumentoFiscal {

    private static final String NAMESPACE_DA_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final String RAIZ_DOCUMENTO = "NFe";
    private static final String RAIZ_DOCUMENTO_AUTORIZADO = "nfeProc";

    private final JAXBContext contexto;
    private final XMLInputFactory fabricaDeLeitura;

    public LeitorDocumentoFiscal() {
        try {
            this.contexto = JAXBContext.newInstance(TNfeProc.class, TNFe.class);
        } catch (JAXBException erro) {
            throw new IllegalStateException(
                    "Não foi possível preparar a leitura de XML a partir das classes geradas do leiaute.", erro);
        }
        this.fabricaDeLeitura = fabricaSemEntidadesExternas();
    }

    /**
     * Lê o documento contido no fluxo informado.
     *
     * <p>O fluxo é consumido, mas não é fechado: quem o abriu continua
     * responsável por fechá-lo.</p>
     *
     * @throws DocumentoFiscalIlegivel se o XML estiver malformado ou se a raiz
     *                                 não for {@code NFe} nem {@code nfeProc} no
     *                                 namespace da NF-e
     */
    public TNFe ler(InputStream conteudo) {
        if (conteudo == null) {
            throw new DocumentoFiscalIlegivel("Não há conteúdo a ler.");
        }

        XMLStreamReader leitorDeFluxo = null;
        try {
            leitorDeFluxo = fabricaDeLeitura.createXMLStreamReader(conteudo);
            avancarAteARaiz(leitorDeFluxo);

            String namespace = leitorDeFluxo.getNamespaceURI();
            String raiz = leitorDeFluxo.getLocalName();
            if (!NAMESPACE_DA_NFE.equals(namespace)) {
                throw new DocumentoFiscalIlegivel(
                        ("O XML não é um documento fiscal eletrônico: a raiz \"%s\" está no namespace "
                                + "\"%s\", e o esperado é \"%s\".")
                                .formatted(raiz, namespace, NAMESPACE_DA_NFE));
            }

            Unmarshaller conversor = contexto.createUnmarshaller();
            return switch (raiz) {
                case RAIZ_DOCUMENTO -> conversor.unmarshal(leitorDeFluxo, TNFe.class).getValue();
                case RAIZ_DOCUMENTO_AUTORIZADO -> documentoDe(conversor.unmarshal(leitorDeFluxo, TNfeProc.class).getValue());
                default -> throw new DocumentoFiscalIlegivel(
                        ("A raiz do XML é \"%s\". Este leitor só processa documento fiscal: \"%s\" ou \"%s\".")
                                .formatted(raiz, RAIZ_DOCUMENTO, RAIZ_DOCUMENTO_AUTORIZADO));
            };
        } catch (XMLStreamException erro) {
            throw new DocumentoFiscalIlegivel("O XML não pôde ser lido: " + erro.getMessage(), erro);
        } catch (JAXBException erro) {
            throw new DocumentoFiscalIlegivel(
                    "O XML não corresponde ao leiaute da NF-e: " + erro.getMessage(), erro);
        } finally {
            fechar(leitorDeFluxo);
        }
    }

    private static TNFe documentoDe(TNfeProc documentoAutorizado) {
        TNFe documento = documentoAutorizado.getNFe();
        if (documento == null) {
            throw new DocumentoFiscalIlegivel(
                    "O envelope <nfeProc> não contém o documento <NFe>.");
        }
        return documento;
    }

    private static void avancarAteARaiz(XMLStreamReader leitorDeFluxo) throws XMLStreamException {
        while (leitorDeFluxo.hasNext()) {
            if (leitorDeFluxo.next() == XMLStreamConstants.START_ELEMENT) {
                return;
            }
        }
        throw new DocumentoFiscalIlegivel("O conteúdo lido não tem elemento nenhum: não é um XML de documento.");
    }

    /**
     * Fábrica de leitura com DTD e entidades externas desligados.
     *
     * <p>O sistema lê arquivo que veio de fora. Com DTD habilitado, um documento
     * pode mandar o processo abrir arquivo local ou fazer requisição de rede
     * durante a análise, e pode se expandir até esgotar a memória.</p>
     */
    private static XMLInputFactory fabricaSemEntidadesExternas() {
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        fabrica.setProperty(XMLInputFactory.IS_COALESCING, true);
        return fabrica;
    }

    private static void fechar(XMLStreamReader leitorDeFluxo) {
        if (leitorDeFluxo == null) {
            return;
        }
        try {
            // Fecha o leitor, não o fluxo de entrada: este pertence a quem chamou.
            leitorDeFluxo.close();
        } catch (XMLStreamException erroAoFechar) {
            // Não há o que fazer aqui, e mascarar o erro original seria pior.
        }
    }
}
