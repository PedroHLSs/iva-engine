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

/**
 * Caso de uso: medir a acurácia do motor contra um gabarito rotulado à mão.
 *
 * <p>É o resultado empírico do trabalho. Tudo o que o sistema faz — ler XML,
 * resolver catálogo por vigência, aplicar regra — é uma afirmação sobre
 * documentos fiscais; este serviço é o que permite dizer <em>quanto</em> dessa
 * afirmação se sustenta, e a única resposta honesta a essa pergunta vem de
 * comparar a saída do motor com o julgamento de uma pessoa.</p>
 *
 * <h2>Roda o motor de novo, e não persiste nada</h2>
 *
 * <p>Poderia parecer mais simples ler os apontamentos já gravados pela última
 * auditoria. Não serve, por duas razões.</p>
 *
 * <p>A primeira é que <strong>o banco não guarda avaliação conforme</strong>. A
 * Etapa 5 grava apontamento, e a Etapa 6 acrescentou as não concluídas; a
 * avaliação que se aplicou e nada encontrou não deixa linha nenhuma — e é
 * justamente ela que forma os verdadeiros negativos e, por diferença, os falsos
 * negativos. Medir pelo banco tornaria metade da matriz de confusão
 * inobservável.</p>
 *
 * <p>A segunda é que <strong>medir não é auditar</strong>. Gravar uma execução a
 * cada medição encheria o histórico de rodadas que ninguém pediu, com o mesmo
 * lote repetido, e o {@code exportar} sem {@code --execucao} passaria a emitir o
 * papel de trabalho de uma medição em vez do da auditoria.</p>
 *
 * <p>O preço é a repetição dos quatro primeiros passos de
 * {@code ServicoDeAuditoria}: abrir o lote, carregar o catálogo, montar o
 * conjunto e rodar o motor. É repetição deliberada — os dois serviços divergem
 * exatamente no passo seguinte, e uni-los exigiria um sinalizador "não grave",
 * que é a forma mais discreta de um dia gravar por engano.</p>
 */
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

    /** Mede a acurácia sobre a origem indicada, sem gravar arquivo nenhum. */
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

    /** Mede a acurácia e grava o relatório no destino indicado. */
    public RelatorioDeAcuracia avaliarEGravar(Path origem, Path gabarito, Path destino) {
        if (destino == null) {
            throw new AvaliacaoDeAcuraciaInvalida("Não foi informado onde gravar o relatório de acurácia.");
        }
        RelatorioDeAcuracia relatorio = avaliar(origem, gabarito);
        escritor.escrever(relatorio, destino);
        return relatorio;
    }

    /** Extensão de arquivo que o escritor configurado produz, sem o ponto. */
    public String extensao() {
        return escritor.extensao();
    }

    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O serviço de avaliação de acurácia precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
