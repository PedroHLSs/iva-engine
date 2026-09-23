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

// Classe que lê o XML de uma NF-e ou NFC-e e devolve a classe gerada pelo JAXB, sem interpretar nada. Aceita a raiz <NFe> e a <nfeProc>, recusa qualquer outra, e desliga DTD e entidades externas, porque o arquivo vem de fora.
public final class LeitorDocumentoFiscal {

    private static final String NAMESPACE_DA_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final String RAIZ_DOCUMENTO = "NFe";
    private static final String RAIZ_DOCUMENTO_AUTORIZADO = "nfeProc";

    private final JAXBContext contexto;
    private final XMLInputFactory fabricaDeLeitura;

    // Construtor que prepara o JAXB uma vez só, que é a parte cara, e a fábrica de leitura segura.
    public LeitorDocumentoFiscal() {
        try {
            this.contexto = JAXBContext.newInstance(TNfeProc.class, TNFe.class);
        } catch (JAXBException erro) {
            throw new IllegalStateException(
                    "Não foi possível preparar a leitura de XML a partir das classes geradas do leiaute.", erro);
        }
        this.fabricaDeLeitura = fabricaSemEntidadesExternas();
    }

    // Lê o documento do fluxo, sem fechá-lo; recusa XML malformado ou com raiz que não seja NFe nem nfeProc.
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

    // Método auxiliar que tira o <NFe> de dentro do <nfeProc>; recusa envelope vazio.
    private static TNFe documentoDe(TNfeProc documentoAutorizado) {
        TNFe documento = documentoAutorizado.getNFe();
        if (documento == null) {
            throw new DocumentoFiscalIlegivel(
                    "O envelope <nfeProc> não contém o documento <NFe>.");
        }
        return documento;
    }

    // Método auxiliar que avança até o primeiro elemento do XML; recusa conteúdo sem elemento.
    private static void avancarAteARaiz(XMLStreamReader leitorDeFluxo) throws XMLStreamException {
        while (leitorDeFluxo.hasNext()) {
            if (leitorDeFluxo.next() == XMLStreamConstants.START_ELEMENT) {
                return;
            }
        }
        throw new DocumentoFiscalIlegivel("O conteúdo lido não tem elemento nenhum: não é um XML de documento.");
    }

    // Método auxiliar que cria a fábrica de leitura com DTD e entidades externas desligados, para o XML não mandar abrir arquivo local ou endereço de rede.
    private static XMLInputFactory fabricaSemEntidadesExternas() {
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        fabrica.setProperty(XMLInputFactory.IS_COALESCING, true);
        return fabrica;
    }

    // Método auxiliar que fecha o leitor de XML, mas não o fluxo de quem chamou.
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
