package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.dominio.Cfop;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Uf;
import br.edu.tcc.auditoria.dominio.excecao.ExcecaoDeDominio;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TCIBS;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TEndereco;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TEnderEmi;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TNFe;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TTribNFe;
import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TUf;
import jakarta.xml.bind.JAXBElement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Classe que converte o que o LeitorDocumentoFiscal leu no modelo de domínio; daqui para dentro não há classe do JAXB, null nem CNPJ ou CPF em texto claro. Campo que não veio vira Optional vazio, e 0 continua zero; o CST, o cClassTrib e a base, que o XML declara uma vez só, são repetidos para IBS e CBS.
public final class NormalizadorDocumento {

    private static final String PREFIXO_DO_IDENTIFICADOR_NA_CHAVE = "NFe";
    private static final String UF_DO_EXTERIOR = "EX";

    private final Pseudonimizador pseudonimizador;

    // Construtor que recebe o pseudonimizador; recusa nulo, porque identificador não entra no domínio em texto claro.
    public NormalizadorDocumento(Pseudonimizador pseudonimizador) {
        if (pseudonimizador == null) {
            throw new IllegalArgumentException(
                    "A normalização exige um pseudonimizador: identificador de participante não entra "
                            + "no domínio em texto claro.");
        }
        this.pseudonimizador = pseudonimizador;
    }

    // Normaliza o documento e os itens; recusa se faltar algo obrigatório ou se um valor não tiver a forma que o domínio exige. Mudou na Etapa 11: ganhou o registro de descrições, sem valor padrão, e a descrição não entra no domínio.
    public DocumentoComItens normalizar(
            TNFe documentoLido, RegistroDeDescricoesDeProduto descricoes) {

        if (descricoes == null) {
            throw new DocumentoFiscalIlegivel(
                    "Não há destino para as descrições de produto. Para descartá-las, passe "
                            + "RegistroDeDescricoesDeProduto.DESCARTA.");
        }
        if (documentoLido == null) {
            throw new DocumentoFiscalIlegivel("Não há documento a normalizar.");
        }
        TNFe.InfNFe informacoes = exigir(documentoLido.getInfNFe(), "infNFe");
        TNFe.InfNFe.Ide identificacao = exigir(informacoes.getIde(), "ide");
        TNFe.InfNFe.Emit emitente = exigir(informacoes.getEmit(), "emit");
        TNFe.InfNFe.Dest destinatario = informacoes.getDest();

        Documento documento = new Documento(
                new ChaveAcesso(chaveDeAcesso(informacoes)),
                exigirTexto(identificacao.getMod(), "ide/mod"),
                exigirTexto(identificacao.getSerie(), "ide/serie"),
                exigirTexto(identificacao.getNNF(), "ide/nNF"),
                dataDeEmissao(identificacao.getDhEmi()),
                ufDoEmitente(emitente),
                ufDoDestinatario(destinatario),
                texto(emitente.getCRT()),
                destinatario == null ? Optional.empty() : texto(destinatario.getIndIEDest()),
                pseudonimizador.pseudonimizar(identificadorDoEmitente(emitente)),
                identificadorDoDestinatario(destinatario).map(pseudonimizador::pseudonimizar));

        return new DocumentoComItens(
                documento, itens(documento.chaveAcesso(), informacoes, descricoes));
    }

    // Método auxiliar que monta os itens e registra a descrição de cada um no mesmo passo, pelo HashDoItem, o mesmo endereço que o acervo usa.
    private static List<ItemDocumento> itens(
            ChaveAcesso chaveAcesso,
            TNFe.InfNFe informacoes,
            RegistroDeDescricoesDeProduto descricoes) {

        List<ItemDocumento> itens = new ArrayList<>();
        for (TNFe.InfNFe.Det detalhamento : informacoes.getDet()) {
            ItemDocumento item = item(detalhamento);
            itens.add(item);
            descricoes.registrar(DescricaoDeProdutoLida.de(
                    HashDoItem.de(chaveAcesso, item), descricaoDeclarada(detalhamento)));
        }
        return itens;
    }

    // Método auxiliar que devolve o xProd como veio, ou null se a nota não o trouxe.
    private static String descricaoDeclarada(TNFe.InfNFe.Det detalhamento) {
        TNFe.InfNFe.Det.Prod produto = detalhamento.getProd();
        return produto == null ? null : produto.getXProd();
    }

    // Método auxiliar que monta um item com os campos de IBS/CBS como vieram.
    private static ItemDocumento item(TNFe.InfNFe.Det detalhamento) {
        TNFe.InfNFe.Det.Prod produto = exigir(detalhamento.getProd(), "det/prod");
        Optional<TTribNFe> grupo = grupoIbsCbs(detalhamento.getImposto());
        Optional<TCIBS> valores = grupo.map(TTribNFe::getGIBSCBS);

        Optional<CodigoCst> cst = grupo.flatMap(declarado -> texto(declarado.getCST())).map(CodigoCst::new);
        Optional<BigDecimal> baseDeCalculo = valores.flatMap(declarado -> decimal(declarado.getVBC()));

        Optional<TCIBS.GIBSUF> ibsEstadual = valores.map(TCIBS::getGIBSUF);
        Optional<TCIBS.GIBSMun> ibsMunicipal = valores.map(TCIBS::getGIBSMun);
        Optional<TCIBS.GCBS> cbs = valores.map(TCIBS::getGCBS);

        return new ItemDocumento(
                numeroDoItem(detalhamento),
                texto(produto.getNCM()).map(Ncm::new),
                texto(produto.getCFOP()).map(Cfop::new),
                exigirDecimal(produto.getVProd(), "det/prod/vProd"),
                // O leiaute declara um CST e um cClassTrib para os dois tributos.
                cst,
                cst,
                grupo.flatMap(declarado -> texto(declarado.getCClassTrib()))
                        .map(CodigoClassificacaoTributaria::new),
                // Mesma coisa com a base de cálculo: vBC é única no grupo.
                baseDeCalculo,
                baseDeCalculo,
                ibsEstadual.flatMap(declarado -> decimal(declarado.getPIBSUF())),
                ibsMunicipal.flatMap(declarado -> decimal(declarado.getPIBSMun())),
                cbs.flatMap(declarado -> decimal(declarado.getPCBS())),
                ibsEstadual.flatMap(declarado -> decimal(declarado.getVIBSUF())),
                ibsMunicipal.flatMap(declarado -> decimal(declarado.getVIBSMun())),
                cbs.flatMap(declarado -> decimal(declarado.getVCBS())));
    }

    // Método auxiliar que acha o grupo IBSCBS entre os tributos do item, pelo nome e pelo tipo.
    private static Optional<TTribNFe> grupoIbsCbs(TNFe.InfNFe.Det.Imposto tributos) {
        if (tributos == null) {
            return Optional.empty();
        }
        for (JAXBElement<?> declarado : tributos.getContent()) {
            if ("IBSCBS".equals(declarado.getName().getLocalPart())
                    && declarado.getValue() instanceof TTribNFe grupo) {
                return Optional.of(grupo);
            }
        }
        return Optional.empty();
    }

    // Método auxiliar que tira a chave de acesso do atributo Id de infNFe, que começa com "NFe".
    private static String chaveDeAcesso(TNFe.InfNFe informacoes) {
        String identificador = exigirTexto(informacoes.getId(), "infNFe/@Id");
        if (!identificador.startsWith(PREFIXO_DO_IDENTIFICADOR_NA_CHAVE)) {
            throw new DocumentoFiscalIlegivel(
                    ("O atributo Id de infNFe deveria começar com \"%s\" seguido da chave de acesso, e o "
                            + "documento não tem outro lugar de onde tirá-la.")
                            .formatted(PREFIXO_DO_IDENTIFICADOR_NA_CHAVE));
        }
        return identificador.substring(PREFIXO_DO_IDENTIFICADOR_NA_CHAVE.length());
    }

    // Método auxiliar que pega a data de emissão como a nota declarou, sem converter fuso, porque é nessa data que a vigência é resolvida.
    private static LocalDate dataDeEmissao(String dataEHoraDeclarada) {
        String declarada = exigirTexto(dataEHoraDeclarada, "ide/dhEmi");
        try {
            return OffsetDateTime.parse(declarada).toLocalDate();
        } catch (DateTimeParseException erro) {
            throw new DocumentoFiscalIlegivel(
                    "A data de emissão declarada em ide/dhEmi não pôde ser interpretada: \"%s\"."
                            .formatted(declarada), erro);
        }
    }

    // Método auxiliar que lê a UF do emitente; recusa se ela não vier.
    private static Uf ufDoEmitente(TNFe.InfNFe.Emit emitente) {
        TEnderEmi endereco = exigir(emitente.getEnderEmit(), "emit/enderEmit");
        if (endereco.getUF() == null) {
            throw new DocumentoFiscalIlegivel("O documento não declara a UF do emitente em emit/enderEmit/UF.");
        }
        return Uf.de(endereco.getUF().value());
    }

    // Método auxiliar que lê a UF do destinatário; destinatário no exterior (EX) ou sem endereço fica vazio.
    private static Optional<Uf> ufDoDestinatario(TNFe.InfNFe.Dest destinatario) {
        if (destinatario == null) {
            return Optional.empty();
        }
        TEndereco endereco = destinatario.getEnderDest();
        if (endereco == null || endereco.getUF() == null) {
            return Optional.empty();
        }
        TUf sigla = endereco.getUF();
        if (UF_DO_EXTERIOR.equals(sigla.value())) {
            return Optional.empty();
        }
        return Optional.of(Uf.de(sigla.value()));
    }

    // Método auxiliar que devolve o CNPJ ou o CPF do emitente; recusa se não houver nenhum.
    private static String identificadorDoEmitente(TNFe.InfNFe.Emit emitente) {
        return texto(emitente.getCNPJ())
                .or(() -> texto(emitente.getCPF()))
                .orElseThrow(() -> new DocumentoFiscalIlegivel(
                        "O documento não identifica o emitente: não há CNPJ nem CPF em emit."));
    }

    // Método auxiliar que devolve o CNPJ, o CPF ou o identificador de estrangeiro do destinatário, se houver.
    private static Optional<String> identificadorDoDestinatario(TNFe.InfNFe.Dest destinatario) {
        if (destinatario == null) {
            return Optional.empty();
        }
        return texto(destinatario.getCNPJ())
                .or(() -> texto(destinatario.getCPF()))
                .or(() -> texto(destinatario.getIdEstrangeiro()));
    }

    // Método auxiliar que lê o número do item; recusa se não for número inteiro.
    private static int numeroDoItem(TNFe.InfNFe.Det detalhamento) {
        String declarado = exigirTexto(detalhamento.getNItem(), "det/@nItem");
        try {
            return Integer.parseInt(declarado);
        } catch (NumberFormatException erro) {
            throw new DocumentoFiscalIlegivel(
                    "O número de item declarado em det/@nItem não é um número inteiro: \"%s\"."
                            .formatted(declarado), erro);
        }
    }

    // Método auxiliar que devolve o texto sem espaços em volta, ou vazio quando não veio ou veio em branco.
    private static Optional<String> texto(String declarado) {
        if (declarado == null) {
            return Optional.empty();
        }
        String semEspacos = declarado.strip();
        return semEspacos.isEmpty() ? Optional.empty() : Optional.of(semEspacos);
    }

    // Método auxiliar que devolve o valor numérico com as casas decimais declaradas, ou vazio.
    private static Optional<BigDecimal> decimal(String declarado) {
        return texto(declarado).map(NormalizadorDocumento::converterEmDecimal);
    }

    // Método auxiliar que exige um texto obrigatório.
    private static String exigirTexto(String declarado, String caminhoNoXml) {
        return texto(declarado).orElseThrow(() -> new DocumentoFiscalIlegivel(
                "O documento não traz \"%s\", que é obrigatório para identificá-lo."
                        .formatted(caminhoNoXml)));
    }

    // Método auxiliar que exige um valor numérico obrigatório.
    private static BigDecimal exigirDecimal(String declarado, String caminhoNoXml) {
        return decimal(declarado).orElseThrow(() -> new DocumentoFiscalIlegivel(
                "O documento não traz \"%s\", que é obrigatório.".formatted(caminhoNoXml)));
    }

    // Método auxiliar que converte o texto em BigDecimal; recusa se não for número.
    private static BigDecimal converterEmDecimal(String declarado) {
        try {
            return new BigDecimal(declarado);
        } catch (NumberFormatException erro) {
            throw new DocumentoFiscalIlegivel(
                    "O documento declara \"%s\" onde deveria haver um valor numérico.".formatted(declarado),
                    erro);
        }
    }

    // Método auxiliar que exige que um grupo do XML exista.
    private static <T> T exigir(T lido, String caminhoNoXml) {
        if (lido == null) {
            throw new DocumentoFiscalIlegivel(
                    "O documento não traz o grupo \"%s\".".formatted(caminhoNoXml));
        }
        return lido;
    }
}
