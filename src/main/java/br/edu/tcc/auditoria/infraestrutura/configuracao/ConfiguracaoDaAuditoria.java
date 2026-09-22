package br.edu.tcc.auditoria.infraestrutura.configuracao;

import br.edu.tcc.auditoria.aplicacao.acuracia.ComparadorDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.EscritorDeRelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.FonteDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.ServicoDeAvaliacaoDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.analise.FabricaDeLeituraDeLote;
import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.RegistroDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.aplicacao.conferencia.ConsultaDaBaseTributaria;
import br.edu.tcc.auditoria.aplicacao.conferencia.MontadorDaConferencia;
import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.auditoria.RepositorioDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.RepositorioDeCargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeItensDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ExportadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MontadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ServicoDeExportacao;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.infraestrutura.xml.RegistroDeDescricoesDeProduto;
import br.edu.tcc.auditoria.dominio.tratativa.RepositorioTratativa;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhasDeLeituraEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorDocumentoFiscal;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorLote;
import br.edu.tcc.auditoria.infraestrutura.xml.NormalizadorDocumento;
import br.edu.tcc.auditoria.infraestrutura.xml.Pseudonimizador;
import br.edu.tcc.auditoria.infraestrutura.sal.ArquivoDeSalLocal;
import br.edu.tcc.auditoria.infraestrutura.sal.ResolvedorDeSal;
import br.edu.tcc.auditoria.infraestrutura.sal.SalResolvido;
import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Montagem dos objetos das camadas de aplicação e domínio.
 *
 * <p>Nenhuma classe de {@code aplicacao} ou {@code dominio} tem anotação de
 * Spring, e nenhuma vai ter (D001). A ligação entre elas é feita aqui, à mão. O
 * arquivo é a fotografia de quem depende de quem — que é justamente o que a
 * injeção por anotação deixa implícito.</p>
 */
@Configuration
public class ConfiguracaoDaAuditoria {

    /**
     * Nome da propriedade que carrega o sal de pseudonimização.
     *
     * <p>Repetido aqui porque a constante equivalente é interna ao pacote
     * {@code infraestrutura.xml}. Serve para que o valor também possa vir de
     * {@code application.properties} ou de argumento de linha de comando, além
     * da propriedade de sistema e da variável de ambiente que
     * {@link SalDeInstalacao} já lê sozinho.</p>
     */
    static final String PROPRIEDADE_DO_SAL = "auditoria.pseudonimizacao.sal";

    /** Nome da propriedade que carrega a tolerância de valor da regra R05. */
    static final String PROPRIEDADE_DA_TOLERANCIA = "auditoria.tolerancia-de-valor";

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /**
     * Sal de pseudonimização, resolvido em cascata.
     *
     * <p><strong>Continua sem sal fixo em código, e não pode ter.</strong> Sal
     * conhecido torna o pseudônimo reversível por força bruta — são poucos bilhões
     * de CNPJ possíveis. O que a Etapa 10 acrescentou não é um padrão em código:
     * é um sal sorteado de 256 bits na primeira subida, gravado fora do
     * repositório, tão secreto quanto um escolhido à mão. Ver
     * {@link ResolvedorDeSal}.</p>
     *
     * <p>A precedência entre a propriedade e a variável de ambiente é a mesma que
     * a Etapa 4 já usava, de propósito: quem instalou antes continua com o mesmo
     * sal, e ninguém é surpreendido por uma troca que o guarda de subida
     * recusaria.</p>
     */
    @Bean
    SalResolvido salResolvido(Environment ambiente) {
        return new ResolvedorDeSal(ArquivoDeSalLocal.doSistemaOperacional())
                .resolver(
                        ambiente.getProperty(PROPRIEDADE_DO_SAL),
                        System.getenv(ResolvedorDeSal.VARIAVEL_DE_AMBIENTE));
    }

    /**
     * O sal em si, para quem só precisa dele.
     *
     * <p>{@code Pseudonimizador} e {@code PseudonimizadorDeChaveComSal} não têm
     * por que saber de onde o sal veio. A procedência interessa ao guarda de
     * subida e ao diagnóstico, que recebem {@link SalResolvido}.</p>
     */
    @Bean
    SalDeInstalacao salDeInstalacao(SalResolvido resolvido) {
        return resolvido.sal();
    }

    /**
     * Tolerância usada pela regra que confere valor de tributo contra base e
     * alíquota.
     *
     * <p><strong>Também sem valor padrão.</strong> Tolerância não é conteúdo
     * normativo — é uma escolha de quem audita sobre quanta diferença de
     * arredondamento não merece apontamento. Escolher por conta própria seria
     * decidir, em nome do usuário, quantos centavos de divergência ficam
     * invisíveis no relatório. Para exigir igualdade exata, configure zero.</p>
     */
    @Bean
    ToleranciaDeValor toleranciaDeValor(Environment ambiente) {
        String configurada = ambiente.getProperty(PROPRIEDADE_DA_TOLERANCIA);
        if (configurada == null || configurada.isBlank()) {
            throw new ConfiguracaoInvalida(
                    ("Não há tolerância de valor configurada. Defina \"%s\" com a diferença máxima que "
                            + "não deve virar apontamento — por exemplo \"0.01\" para um centavo, ou "
                            + "\"0\" para exigir igualdade exata. O sistema não escolhe por você: essa "
                            + "escolha decide quanta divergência some do relatório.")
                            .formatted(PROPRIEDADE_DA_TOLERANCIA));
        }
        try {
            return ToleranciaDeValor.de(new BigDecimal(configurada.strip()));
        } catch (NumberFormatException naoENumero) {
            throw new ConfiguracaoInvalida(
                    "A propriedade \"%s\" não é um número: \"%s\".".formatted(
                            PROPRIEDADE_DA_TOLERANCIA, configurada),
                    naoENumero);
        }
    }

    @Bean
    Pseudonimizador pseudonimizador(SalDeInstalacao sal) {
        return new Pseudonimizador(sal);
    }

    @Bean
    LeitorDocumentoFiscal leitorDocumentoFiscal() {
        return new LeitorDocumentoFiscal();
    }

    @Bean
    NormalizadorDocumento normalizadorDocumento(Pseudonimizador pseudonimizador) {
        return new NormalizadorDocumento(pseudonimizador);
    }

    @Bean
    FalhasDeLeituraEmMemoria falhasDeLeitura() {
        return new FalhasDeLeituraEmMemoria();
    }

    @Bean
    LeitorLote leitorLote(
            LeitorDocumentoFiscal leitor,
            NormalizadorDocumento normalizador,
            FalhasDeLeituraEmMemoria falhas) {
        // A CLI nao grava item_da_execucao, entao nao ha linha onde a descricao
        // caberia. Guarda-la em memoria aqui seria acumular pelo tempo do
        // processo um texto que ninguem le. Ver RegistroDeDescricoesDeProduto.
        return new LeitorLote(
                leitor, normalizador, falhas, RegistroDeDescricoesDeProduto.DESCARTA);
    }

    @Bean
    MotorAuditoria motorAuditoria() {
        return new MotorAuditoria();
    }

    @Bean
    ServicoDeAuditoria servicoDeAuditoria(
            FonteDeLoteDeDocumentos fonte,
            ProvedorDeCatalogo provedorDeCatalogo,
            RepositorioDaAuditoria repositorio,
            MotorAuditoria motor,
            ToleranciaDeValor tolerancia,
            Clock relogio) {
        return new ServicoDeAuditoria(fonte, provedorDeCatalogo, repositorio, motor, tolerancia, relogio);
    }

    /**
     * Analisar é auditar mais duas coisas.
     *
     * <p><strong>Emenda da Etapa 11 a este arquivo, que é da Etapa 5.</strong> O
     * {@code @Bean} acima continua idêntico e continua servindo o comando
     * {@code auditar}: nada da CLI mudou. Este aqui é outro ponto de entrada,
     * para a análise enviada pela web, e reusa o mesmo pipeline — ele não
     * constrói leitor, normalizador, motor nem repositório próprios.</p>
     *
     * <p>Repare que ele <em>não</em> recebe {@code FonteDeLoteDeDocumentos}, e
     * sim uma fábrica: cada análise precisa da própria, para que os arquivos
     * ilegíveis de um lote não apareçam no resultado do seguinte. Ver
     * {@code LeituraDeLote}.</p>
     */
    @Bean
    ServicoDeAnalise servicoDeAnalise(
            FabricaDeLeituraDeLote leituras,
            ProvedorDeCatalogo provedorDeCatalogo,
            RepositorioDaAuditoria repositorio,
            MotorAuditoria motor,
            ToleranciaDeValor tolerancia,
            Clock relogio,
            RegistroDoAcervoDaAnalise acervo) {
        return new ServicoDeAnalise(
                leituras, provedorDeCatalogo, repositorio, motor, tolerancia, relogio, acervo);
    }

    /**
     * Reconstrói os quatro estados a partir do que foi gravado.
     *
     * <p>Acrescentado na Etapa 11. Não roda o motor de novo: lê apontamento,
     * pendência e a lista de regras aplicadas, e deriva o conforme por
     * subtração sobre conjuntos integralmente gravados.</p>
     */
    @Bean
    MontadorDaConferencia montadorDaConferencia(
            ConsultaDeExecucoes execucoes,
            ConsultaDeItensDaExecucao itens,
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDoAcervoDaAnalise acervo,
            ConsultaDeDocumentos documentos,
            ProvedorDeCatalogoPorVersao catalogos) {
        return new MontadorDaConferencia(
                execucoes, itens, achados, naoAvaliadas, acervo, documentos, catalogos);
    }

    /**
     * A consulta da base tributária carregada, resolvida numa data explícita.
     *
     * <p>Acrescentado na Etapa 11. É o caso de uso separado que a D003 admitiu:
     * a data vem de quem pergunta, e nunca de dentro.</p>
     */
    @Bean
    ConsultaDaBaseTributaria consultaDaBaseTributaria(
            RepositorioDeCargaDeCatalogo cargas, ProvedorDeCatalogoPorVersao catalogos) {
        return new ConsultaDaBaseTributaria(cargas, catalogos);
    }

    @Bean
    ServicoDeImportacaoDeCatalogo servicoDeImportacaoDeCatalogo(
            RepositorioDeCargaDeCatalogo repositorio) {
        return new ServicoDeImportacaoDeCatalogo(repositorio);
    }

    @Bean
    ServicoDeTratativa servicoDeTratativa(
            ConsultaDeAchados consulta, RepositorioTratativa repositorio, Clock relogio) {
        return new ServicoDeTratativa(consulta, repositorio, relogio);
    }

    @Bean
    MontadorDePapelDeTrabalho montadorDePapelDeTrabalho(
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador) {
        return new MontadorDePapelDeTrabalho(achados, naoAvaliadas, documentos, pseudonimizador);
    }

    @Bean
    ServicoDeExportacao servicoDeExportacao(
            ConsultaDeExecucoes execucoes,
            MontadorDePapelDeTrabalho montador,
            ExportadorDePapelDeTrabalho exportador) {
        return new ServicoDeExportacao(execucoes, montador, exportador);
    }

    @Bean
    ComparadorDeGabarito comparadorDeGabarito() {
        return new ComparadorDeGabarito();
    }

    /**
     * Harness de avaliação de acurácia.
     *
     * <p>Recebe os mesmos insumos de {@link #servicoDeAuditoria} menos o
     * repositório, e é essa ausência que o define: a medição roda o motor e não
     * grava nada. Ver D008.</p>
     */
    @Bean
    ServicoDeAvaliacaoDeAcuracia servicoDeAvaliacaoDeAcuracia(
            FonteDeLoteDeDocumentos fonte,
            ProvedorDeCatalogo provedorDeCatalogo,
            FonteDeGabarito fonteDeGabarito,
            MotorAuditoria motor,
            ComparadorDeGabarito comparador,
            EscritorDeRelatorioDeAcuracia escritor,
            ToleranciaDeValor tolerancia) {
        return new ServicoDeAvaliacaoDeAcuracia(
                fonte, provedorDeCatalogo, fonteDeGabarito, motor, comparador, escritor, tolerancia);
    }
}
