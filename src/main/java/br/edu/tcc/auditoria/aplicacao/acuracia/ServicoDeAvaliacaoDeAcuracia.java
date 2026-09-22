package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.LoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;

import java.nio.file.Path;
import java.util.List;

// Serviço que avalia a acurácia de um motor de auditoria, comparando suas avaliações com um gabarito.
public final class ServicoDeAvaliacaoDeAcuracia {

    private final FonteDeLoteDeDocumentos fonte;
    private final ProvedorDeCatalogo provedorDeCatalogo;
    private final FonteDeGabarito fonteDeGabarito;
    private final MotorAuditoria motor;
    private final ComparadorDeGabarito comparador;
    private final EscritorDeRelatorioDeAcuracia escritor;
    private final ToleranciaDeValor tolerancia;

    public ServicoDeAvaliacaoDeAcuracia(
            FonteDeLoteDeDocumentos fonte,
            ProvedorDeCatalogo provedorDeCatalogo,
            FonteDeGabarito fonteDeGabarito,
            MotorAuditoria motor,
            ComparadorDeGabarito comparador,
            EscritorDeRelatorioDeAcuracia escritor,
            ToleranciaDeValor tolerancia) {

        this.fonte = exigir(fonte, "a fonte de documentos");
        this.provedorDeCatalogo = exigir(provedorDeCatalogo, "o provedor de catálogo");
        this.fonteDeGabarito = exigir(fonteDeGabarito, "a fonte de gabarito");
        this.motor = exigir(motor, "o motor de auditoria");
        this.comparador = exigir(comparador, "o comparador de gabarito");
        this.escritor = exigir(escritor, "o escritor do relatório");
        this.tolerancia = exigir(tolerancia, "a tolerância de valor");
    }

    // Mede a acurácia do motor de auditoria, comparando suas avaliações com o gabarito fornecido.
    public RelatorioDeAcuracia avaliar(Path origem, Path gabarito) {
        if (origem == null) {
            throw new AvaliacaoDeAcuraciaInvalida("Não há origem de documentos sobre a qual medir.");
        }
        if (gabarito == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "Não há gabarito com que comparar. Sem verdade de referência não existe acurácia, "
                            + "só contagem de apontamentos.");
        }

        Gabarito rotulado = fonteDeGabarito.carregar(gabarito);
        if (rotulado == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A fonte de gabarito não devolveu gabarito para \"%s\".".formatted(gabarito));
        }
        if (rotulado.vazio()) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    ("O gabarito \"%s\" não tem nenhuma linha rotulada. Medir contra ele produziria um "
                            + "relatório de métricas indefinidas com aparência de medição feita.")
                            .formatted(gabarito));
        }

        LoteDeDocumentos lote = fonte.abrir(origem);
        if (lote == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A fonte de documentos não devolveu lote para \"%s\".".formatted(origem));
        }

        CatalogoParaAuditoria catalogo = provedorDeCatalogo.carregar();
        if (catalogo == null) {
            throw new AvaliacaoDeAcuraciaInvalida("O provedor de catálogo não devolveu catálogo.");
        }

        ConjuntoRegras conjunto = ConjuntoRegras.padrao(catalogo.cobertura(), tolerancia);
        List<Avaliacao> avaliacoes = motor.auditar(lote.documentos(), catalogo, conjunto);

        return comparador.comparar(
                rotulado,
                avaliacoes,
                conjunto.identificadores(),
                catalogo.versao(),
                conjunto.versao(),
                lote.documentos().size(),
                lote.quantidadeDeItens());
    }

    public RelatorioDeAcuracia avaliarEGravar(Path origem, Path gabarito, Path destino) {
        if (destino == null) {
            throw new AvaliacaoDeAcuraciaInvalida("Não foi informado onde gravar o relatório de acurácia.");
        }
        RelatorioDeAcuracia relatorio = avaliar(origem, gabarito);
        escritor.escrever(relatorio, destino);
        return relatorio;
    }

    public String extensao() {
        return escritor.extensao();
    }

    // Retorna o relatório de acurácia do motor de auditoria, comparando suas avaliações com o gabarito fornecido.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O serviço de avaliação de acurácia precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
