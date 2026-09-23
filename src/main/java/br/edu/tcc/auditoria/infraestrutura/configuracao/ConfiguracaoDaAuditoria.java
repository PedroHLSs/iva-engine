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

// Classe de configuração do Spring que monta à mão os objetos de aplicacao e dominio, que não têm anotação nenhuma. O arquivo mostra quem depende de quem.
@Configuration
public class ConfiguracaoDaAuditoria {

    // Nome da propriedade do sal; repetido aqui para o sal também poder vir do application.properties ou da linha de comando.
    static final String PROPRIEDADE_DO_SAL = "auditoria.pseudonimizacao.sal";

    // Nome da propriedade da tolerância de valor da regra R05.
    static final String PROPRIEDADE_DA_TOLERANCIA = "auditoria.tolerancia-de-valor";

    // Relógio em UTC usado para registrar data e hora.
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    // Resolve o sal em cascata: propriedade, variável de ambiente, arquivo local e, por último, um sal sorteado e gravado fora do repositório. Nunca há sal fixo no código, porque sal conhecido deixaria descobrir o CNPJ.
    @Bean
    SalResolvido salResolvido(Environment ambiente) {
        return new ResolvedorDeSal(ArquivoDeSalLocal.doSistemaOperacional())
                .resolver(
                        ambiente.getProperty(PROPRIEDADE_DO_SAL),
                        System.getenv(ResolvedorDeSal.VARIAVEL_DE_AMBIENTE));
    }

    // Entrega só o sal, para quem não precisa saber de onde ele veio.
    @Bean
    SalDeInstalacao salDeInstalacao(SalResolvido resolvido) {
        return resolvido.sal();
    }

    // Lê a tolerância de valor da regra R05. Não tem valor padrão: quanta diferença vira apontamento é escolha de quem audita, e zero exige igualdade exata.
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

    // Cria o pseudonimizador com o sal da instalação.
    @Bean
    Pseudonimizador pseudonimizador(SalDeInstalacao sal) {
        return new Pseudonimizador(sal);
    }

    // Cria o leitor de XML de documento fiscal.
    @Bean
    LeitorDocumentoFiscal leitorDocumentoFiscal() {
        return new LeitorDocumentoFiscal();
    }

    // Cria o normalizador, que transforma o XML lido em Documento com os participantes pseudonimizados.
    @Bean
    NormalizadorDocumento normalizadorDocumento(Pseudonimizador pseudonimizador) {
        return new NormalizadorDocumento(pseudonimizador);
    }

    // Cria o registro, em memória, dos arquivos que não puderam ser lidos.
    @Bean
    FalhasDeLeituraEmMemoria falhasDeLeitura() {
        return new FalhasDeLeituraEmMemoria();
    }

    // Cria o leitor de lote usado pela linha de comando.
    @Bean
    LeitorLote leitorLote(
            LeitorDocumentoFiscal leitor,
            NormalizadorDocumento normalizador,
            FalhasDeLeituraEmMemoria falhas) {
        // A linha de comando não grava a descrição do produto, então ela é descartada em vez de ficar acumulada na memória.
        return new LeitorLote(
                leitor, normalizador, falhas, RegistroDeDescricoesDeProduto.DESCARTA);
    }

    // Cria o motor que aplica as regras.
    @Bean
    MotorAuditoria motorAuditoria() {
        return new MotorAuditoria();
    }

    // Cria o serviço de auditoria usado pelo comando auditar.
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

    // Cria o serviço de análise da web, que usa o mesmo caminho do auditar e recebe uma fábrica de leitura, para os ilegíveis de uma análise não aparecerem na próxima. Acrescentado na Etapa 11, sem mudar o bean do auditar.
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

    // Cria o montador da conferência, que refaz os quatro estados a partir do que foi gravado, sem rodar o motor de novo. Acrescentado na Etapa 11.
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

    // Cria a consulta da base tributária, sempre numa data informada por quem pergunta. Acrescentado na Etapa 11.
    @Bean
    ConsultaDaBaseTributaria consultaDaBaseTributaria(
            RepositorioDeCargaDeCatalogo cargas, ProvedorDeCatalogoPorVersao catalogos) {
        return new ConsultaDaBaseTributaria(cargas, catalogos);
    }

    // Cria o serviço de importação de catálogo.
    @Bean
    ServicoDeImportacaoDeCatalogo servicoDeImportacaoDeCatalogo(
            RepositorioDeCargaDeCatalogo repositorio) {
        return new ServicoDeImportacaoDeCatalogo(repositorio);
    }

    // Cria o serviço de tratativa de achados.
    @Bean
    ServicoDeTratativa servicoDeTratativa(
            ConsultaDeAchados consulta, RepositorioTratativa repositorio, Clock relogio) {
        return new ServicoDeTratativa(consulta, repositorio, relogio);
    }

    // Cria o montador do papel de trabalho.
    @Bean
    MontadorDePapelDeTrabalho montadorDePapelDeTrabalho(
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador) {
        return new MontadorDePapelDeTrabalho(achados, naoAvaliadas, documentos, pseudonimizador);
    }

    // Cria o serviço de exportação da planilha.
    @Bean
    ServicoDeExportacao servicoDeExportacao(
            ConsultaDeExecucoes execucoes,
            MontadorDePapelDeTrabalho montador,
            ExportadorDePapelDeTrabalho exportador) {
        return new ServicoDeExportacao(execucoes, montador, exportador);
    }

    // Cria o comparador entre o gabarito e as avaliações do motor.
    @Bean
    ComparadorDeGabarito comparadorDeGabarito() {
        return new ComparadorDeGabarito();
    }

    // Cria o serviço de avaliação de acurácia. Recebe o mesmo que o serviço de auditoria, menos o repositório: a medição roda o motor e não grava nada.
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
