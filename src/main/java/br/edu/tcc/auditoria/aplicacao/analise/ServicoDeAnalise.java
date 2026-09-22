package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.RepositorioDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

// Serviço que analisa um lote de documentos, usando o motor de auditoria e registrando os resultados.
public final class ServicoDeAnalise {

    private final FabricaDeLeituraDeLote leituras;
    private final ProvedorDeCatalogo provedorDeCatalogo;
    private final RepositorioDaAuditoria repositorio;
    private final MotorAuditoria motor;
    private final ToleranciaDeValor tolerancia;
    private final Clock relogio;
    private final RegistroDoAcervoDaAnalise acervo;

    public ServicoDeAnalise(
            FabricaDeLeituraDeLote leituras,
            ProvedorDeCatalogo provedorDeCatalogo,
            RepositorioDaAuditoria repositorio,
            MotorAuditoria motor,
            ToleranciaDeValor tolerancia,
            Clock relogio,
            RegistroDoAcervoDaAnalise acervo) {

        this.leituras = exigir(leituras, "a fábrica de leitura de lote");
        this.provedorDeCatalogo = exigir(provedorDeCatalogo, "o provedor de catálogo");
        this.repositorio = exigir(repositorio, "o repositório da auditoria");
        this.motor = exigir(motor, "o motor de auditoria");
        this.tolerancia = exigir(tolerancia, "a tolerância de valor");
        this.relogio = exigir(relogio, "o relógio");
        this.acervo = exigir(acervo, "o registro do acervo da análise");
    }

    // Analisa um lote de documentos a partir de uma origem, registrando os resultados e arquivos ilegíveis.
    public ResultadoDaAnalise analisar(Path origem) {
        if (origem == null) {
            throw new AnaliseInvalida("Não há origem a analisar.");
        }

        LeituraDeLote leitura = leituras.nova();
        if (leitura == null) {
            throw new AnaliseInvalida("A fábrica não devolveu leitura de lote.");
        }

        ServicoDeAuditoria auditoria = new ServicoDeAuditoria(
                leitura.fonte(), provedorDeCatalogo, repositorio, motor, tolerancia, relogio);

        ResultadoDaAuditoria resultado = auditoria.auditar(origem);
        List<ArquivoIlegivel> ilegiveis = leitura.arquivosIlegiveis();

        acervo.registrar(resultado.execucao().id(), itensLidos(resultado, leitura), ilegiveis);

        return new ResultadoDaAnalise(resultado, ilegiveis);
    }

    // Retorna a lista de itens lidos durante a análise, associando cada item à sua descrição de produto.
    private static List<ItemDaAnalise> itensLidos(
            ResultadoDaAuditoria resultado, LeituraDeLote leitura) {

        List<ItemDaAnalise> itens = new ArrayList<>();
        for (DocumentoComItens documento : resultado.documentos()) {
            for (ItemDocumento item : documento.itensOrdenados()) {
                // O mesmo cálculo que a leitura usou para endereçar a descrição:
                // mesma função, mesmas entradas. É isso que torna impossível
                // associar a descrição ao produto errado.
                HashDoItem resumo = HashDoItem.de(documento.documento().chaveAcesso(), item);
                itens.add(new ItemDaAnalise(
                        documento.documento().chaveAcesso(),
                        item.numeroItem(),
                        resumo,
                        leitura.descricaoDe(resumo)));
            }
        }
        return List.copyOf(itens);
    }

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new AnaliseInvalida("O serviço de análise precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
