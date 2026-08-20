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

/**
 * Converte o objeto de leiaute produzido pelo {@link LeitorDocumentoFiscal} no
 * modelo de domínio.
 *
 * <p>É a fronteira do sistema. Daqui para dentro não existem classes geradas
 * pelo JAXB, não existe {@code null} representando ausência e não existe
 * identificador de participante em texto claro.</p>
 *
 * <h2>Ausente não vira zero</h2>
 *
 * <p>Todo campo que pode não vir no XML atravessa como {@code Optional}: campo
 * ausente e campo com espaço em branco viram {@code Optional.empty()}, e campo
 * declarado como {@code 0} vira {@code Optional.of(BigDecimal.ZERO)}. São
 * situações fiscalmente distintas e produzem apontamentos distintos — ver D002
 * em {@code docs/DECISOES-ARQUITETURA.md}.</p>
 *
 * <p>Os valores monetários são construídos direto do texto do XML, o que
 * preserva a escala declarada: {@code "0"} e {@code "0.00"} continuam
 * distinguíveis depois de normalizados.</p>
 *
 * <h2>Onde o leiaute traz um campo e o domínio tem dois</h2>
 *
 * <p>O grupo {@code IBSCBS} do item declara um único {@code CST}, um único
 * {@code cClassTrib} e uma única base de cálculo {@code vBC}, válidos para os
 * dois tributos. O modelo de domínio tem campo separado para IBS e para CBS.
 * A normalização repete o valor declarado nos dois campos, porque foi isso que o
 * documento afirmou dos dois tributos. Deixar o lado da CBS vazio faria as
 * regras apontarem ausência de campo que o documento preencheu.</p>
 *
 * <h2>O que faz o documento inteiro ser recusado</h2>
 *
 * <p>Quando um valor lido não tem sequer a forma que o domínio exige — chave com
 * 43 dígitos, NCM com 2 — a exceção de domínio sobe e o documento não é
 * normalizado. Não há conserto silencioso e não há descarte de campo: um
 * documento que o modelo não consegue representar fielmente é registrado como
 * falha e fica visível, em vez de entrar no relatório pela metade.</p>
 */
public final class NormalizadorDocumento {

    private static final String PREFIXO_DO_IDENTIFICADOR_NA_CHAVE = "NFe";
    private static final String UF_DO_EXTERIOR = "EX";

    private final Pseudonimizador pseudonimizador;

    public NormalizadorDocumento(Pseudonimizador pseudonimizador) {
        if (pseudonimizador == null) {
            throw new IllegalArgumentException(
                    "A normalização exige um pseudonimizador: identificador de participante não entra "
                            + "no domínio em texto claro.");
        }
        this.pseudonimizador = pseudonimizador;
    }

    /**
     * Normaliza o documento e os seus itens.
     *
     * @throws DocumentoFiscalIlegivel se faltar no XML algo sem o que o
     *                                 documento não pode ser representado
     * @throws ExcecaoDeDominio        se algum valor declarado não tiver a forma
     *                                 que o domínio exige
     */
    public DocumentoComItens normalizar(TNFe documentoLido) {
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

        return new DocumentoComItens(documento, itens(informacoes));
    }

    private static List<ItemDocumento> itens(TNFe.InfNFe informacoes) {
        List<ItemDocumento> itens = new ArrayList<>();
        for (TNFe.InfNFe.Det detalhamento : informacoes.getDet()) {
            itens.add(item(detalhamento));
        }
        return itens;
    }

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

    /**
     * Localiza o grupo {@code IBSCBS} entre os tributos declarados no item.
     *
     * <p>O XSD reúne ICMS, IPI, PIS, COFINS, IS e IBSCBS numa mesma escolha
     * repetível, e o XJC representa isso como uma lista única de elementos. Aqui
     * o grupo é procurado por nome e por tipo: nome porque é o que o documento
     * declarou, tipo porque é o que garante a conversão adiante.</p>
     */
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

    /**
     * Converte a data e hora de emissão na data do documento.
     *
     * <p>Fica a data local declarada, sem conversão de fuso: a auditoria resolve
     * a vigência das tabelas normativas na data em que o documento diz ter sido
     * emitido, e não na data em que aquele instante caiu em outro fuso.</p>
     */
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

    private static Uf ufDoEmitente(TNFe.InfNFe.Emit emitente) {
        TEnderEmi endereco = exigir(emitente.getEnderEmit(), "emit/enderEmit");
        if (endereco.getUF() == null) {
            throw new DocumentoFiscalIlegivel("O documento não declara a UF do emitente em emit/enderEmit/UF.");
        }
        return Uf.de(endereco.getUF().value());
    }

    /**
     * UF do destinatário, quando há destinatário com endereço no país.
     *
     * <p>Destinatário no exterior é declarado com a sigla {@code EX}, que não é
     * unidade federativa. O domínio representa esse caso com ausência, e não com
     * uma sigla especial.</p>
     */
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

    private static String identificadorDoEmitente(TNFe.InfNFe.Emit emitente) {
        return texto(emitente.getCNPJ())
                .or(() -> texto(emitente.getCPF()))
                .orElseThrow(() -> new DocumentoFiscalIlegivel(
                        "O documento não identifica o emitente: não há CNPJ nem CPF em emit."));
    }

    private static Optional<String> identificadorDoDestinatario(TNFe.InfNFe.Dest destinatario) {
        if (destinatario == null) {
            return Optional.empty();
        }
        return texto(destinatario.getCNPJ())
                .or(() -> texto(destinatario.getCPF()))
                .or(() -> texto(destinatario.getIdEstrangeiro()));
    }

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

    /**
     * Texto declarado, ou ausência.
     *
     * <p>Espaço em branco em volta é removido, e campo em branco vira ausência:
     * o domínio recusa texto vazio de propósito, para que ausência tenha uma
     * grafia só.</p>
     */
    private static Optional<String> texto(String declarado) {
        if (declarado == null) {
            return Optional.empty();
        }
        String semEspacos = declarado.strip();
        return semEspacos.isEmpty() ? Optional.empty() : Optional.of(semEspacos);
    }

    /** Valor declarado, com a escala em que foi declarado, ou ausência. */
    private static Optional<BigDecimal> decimal(String declarado) {
        return texto(declarado).map(NormalizadorDocumento::converterEmDecimal);
    }

    private static String exigirTexto(String declarado, String caminhoNoXml) {
        return texto(declarado).orElseThrow(() -> new DocumentoFiscalIlegivel(
                "O documento não traz \"%s\", que é obrigatório para identificá-lo."
                        .formatted(caminhoNoXml)));
    }

    private static BigDecimal exigirDecimal(String declarado, String caminhoNoXml) {
        return decimal(declarado).orElseThrow(() -> new DocumentoFiscalIlegivel(
                "O documento não traz \"%s\", que é obrigatório.".formatted(caminhoNoXml)));
    }

    private static BigDecimal converterEmDecimal(String declarado) {
        try {
            return new BigDecimal(declarado);
        } catch (NumberFormatException erro) {
            throw new DocumentoFiscalIlegivel(
                    "O documento declara \"%s\" onde deveria haver um valor numérico.".formatted(declarado),
                    erro);
        }
    }

    private static <T> T exigir(T lido, String caminhoNoXml) {
        if (lido == null) {
            throw new DocumentoFiscalIlegivel(
                    "O documento não traz o grupo \"%s\".".formatted(caminhoNoXml));
        }
        return lido;
    }
}
